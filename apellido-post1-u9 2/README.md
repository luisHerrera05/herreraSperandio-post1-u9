# Recordatorios Citas Service

![CI](https://github.com/USUARIO/apellido-post1-u9/actions/workflows/ci.yml/badge.svg)

## Descripción
Post-contenido de la Unidad 9 (*Patrones de Diseño de Software*, Sexto Semestre): **Probar el diseño con JUnit 5, Mockito, Test Doubles e Integración Continua**.

Es un único proyecto Spring Boot (Java 21, Maven) que revisa qué citas médicas ocurren en las próximas 24 horas y no han sido notificadas, y envía un recordatorio por el canal preferido del paciente (correo o SMS). También permite enviar un recordatorio **urgente** (cita en menos de 2 horas) por SMS.

El servicio central, `CitaReminderServiceImpl`, nace con dos patrones y con DIP:

| Elemento | Rol |
|---|---|
| `ReminderChannel` (+ `EmailReminderChannel`, `SmsReminderChannel`) | **Strategy** (Unidad 4): cada canal es una implementación intercambiable de `enviar(cita, mensaje)` |
| `ReminderChannelFactory` (+ `Impl`) | **Factory Method** (Unidad 2): elige la Strategy según `CanalNotificacion` |
| Inyección por constructor de repositorio, factory, canal urgente y `Clock` | **DIP**: el servicio depende de abstracciones; la construcción concreta ocurre en Spring (`config/`) |

```
src/main/java/com/clinica/recordatorios/
├── domain/       Cita, CanalNotificacion
├── repository/   CitaRepository
├── channel/      ReminderChannel, Email/Sms, ReminderChannelFactory(+Impl), gateway/
├── service/      CitaReminderService(+Impl), CitaNoEncontradaException
└── config/       ClockConfig, CanalUrgenteConfig   ← composition root
```

## Ejecutar
```bash
mvn test      # compila y ejecuta TODAS las pruebas (unitarias e integración)
mvn verify    # lo anterior + reporte de cobertura JaCoCo en target/site/jacoco/index.html
mvn spring-boot:run
```
> Nota: no se configuró Failsafe, por lo que Surefire ejecuta también `CitaReminderServiceIntegrationTest` (su nombre termina en `Test`). Por eso `mvn test` ya incluye las pruebas de integración; `mvn verify` añade el reporte de cobertura.

## Pruebas incluidas
| Clase | Tipo | Qué verifica |
|---|---|---|
| `service/CitaReminderServiceImplTest` | Unitaria (Mockito) | Selección de canal, canales distintos por cita, ventana exacta de 24 h con `Clock` fijo, ausencia de citas, y los tres escenarios del envío urgente (éxito, fallo del gateway, cita inexistente) |
| `channel/SmsReminderChannelTest` | Unitaria | `SmsReminderChannel` delega en el gateway con número y texto correctos |
| `channel/ReminderChannelFactoryImplTest` | Unitaria | La Factory devuelve la Strategy correcta por cada `CanalNotificacion` |
| `repository/CitaRepositoryTest` | `@DataJpaTest` + H2 | La consulta derivada excluye citas ya enviadas y fuera de ventana |
| `CitaReminderServiceIntegrationTest` | `@SpringBootTest` + H2 | Flujo completo con contexto real, `Clock` fijo y `NotificadorSmsGateway` mockeado |

## Decisiones de diseño

### 1. Mock, Stub o Fake para cada colaborador (Parte 1)

Criterio aplicado (tabla de Test Doubles de la guía): un **Stub** solo devuelve respuestas enlatadas; un **Mock** además permite *verificar interacciones*; un **Fake** es una implementación real y funcional, simplificada. Pregunta clave: ¿la prueba necesita comprobar *que se llamó* a algo, o solo controlar lo que devuelve?

| Colaborador | Double | Justificación |
|---|---|---|
| `CitaRepository` | **Mock** | No basta con controlar lo que devuelve la consulta (parte stub). El comportamiento bajo prueba incluye un efecto: `save(cita)` debe llamarse exactamente una vez con la cita marcada como enviada, y **nunca** si no hay citas. Eso solo se comprueba verificando la interacción. Se usa además `ArgumentCaptor` para verificar los límites exactos de la consulta. |
| `ReminderChannelFactory` y `ReminderChannel` | **Mock** | Lo que se prueba es precisamente la *selección y uso de la estrategia*: que el servicio pida el canal `EMAIL` para una cita por correo y que ese canal reciba la cita y el mensaje. No hay valor de retorno que comparar (`enviar` es `void`); el único modo de observarlo es verificar la interacción. |
| `Clock` | **Fake** (`Clock.fixed(...)`) | `Clock.fixed` es una implementación real y completa provista por el JDK, que se comporta igual que en producción salvo por tener el instante congelado. Mockear `Clock` obligaría a stubbear `instant()` y `getZone()` y arriesgaría que `LocalDateTime.now(clock)` no se comporte como con un reloj real. Con el fake, la aserción de la ventana (`09:00` → `09:00` del día siguiente) es exacta y reproducible en cualquier máquina y a cualquier hora. |

### 2. Qué patrón facilitó qué prueba

**Strategy (`ReminderChannel`).** Sin la interfaz, la lógica de envío (cliente SMTP, llamada HTTP a un proveedor de SMS) estaría escrita dentro del servicio, y probar la selección de canal exigiría mockear librerías de bajo nivel. Con Strategy basta un `@Mock ReminderChannel` con un único método `enviar()` para verificar cualquier canal. Prueba beneficiada: `enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido` y `..._citasConCanalesDistintos_cadaUnaUsaSuPropioCanal`. Además, `SmsReminderChannelTest` se escribe con un solo mock (`NotificadorSmsGateway`).

**Factory Method (`ReminderChannelFactory`).** Sin ella, el servicio tendría un `if/else` o `switch` embebido y la decisión solo sería observable de forma indirecta (qué se imprimió o qué se envió realmente). Con la Factory inyectada, la selección es una interacción explícita y verificable: `verify(reminderChannelFactory).obtenerCanal(EMAIL)`. Además es una unidad pequeña y aislada: `ReminderChannelFactoryImplTest` comprueba el mapeo en dos líneas por caso.

**DIP + inyección por constructor.** Permite sustituir los cuatro colaboradores en cada prueba: mocks para repositorio y canales, fake para el reloj, y en integración un `@MockBean` para el gateway.

### 3. El refactor DIP (Parte 2): antes y después

**Diagnóstico — por qué `enviarRecordatorioUrgente` no era testeable.** En la primera versión, la línea responsable era:

```java
ReminderChannel canalUrgente = new SmsReminderChannel(new TwilioNotificadorSmsGateway());
```

`SmsReminderChannel` y `TwilioNotificadorSmsGateway` se construían con `new` **dentro del cuerpo del método**. El método quedaba acoplado a clases concretas: la prueba no tenía ningún punto donde insertar un test double, así que era imposible escribir "el gateway de SMS falla, por lo tanto la cita no debe marcarse como enviada" sin ejecutar el código real. Contraste: `enviarRecordatoriosPendientes()` sí era testeable desde la Parte 1 porque recibía la `Factory` por constructor (una abstracción que la prueba puede reemplazar) y no construía ningún colaborador. Técnicamente se podría interceptar el `new` con `Mockito.mockConstruction()` (Mockito 5, mock maker inline), pero eso trata el síntoma con una herramienta más potente: el problema es de diseño, y se resuelve con DIP.

| Antes (viola DIP) | Después (DIP) |
|---|---|
| `new SmsReminderChannel(new TwilioNotificadorSmsGateway())` dentro del método | `canalUrgente` recibido por constructor (`@Qualifier("canalUrgente") ReminderChannel`) |
| El servicio conoce clases concretas | El servicio solo conoce la abstracción `ReminderChannel` |
| No hay punto de sustitución para Mockito | La prueba inyecta `canalUrgenteMock` |
| Escenario de fallo del gateway: imposible de probar | `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` |

**Solución.** El `new` no se elimina: se *mueve* al único lugar donde corresponde, el composition root (`config/CanalUrgenteConfig`), que expone el bean `canalUrgente`. El comportamiento externo no cambió; solo cambió quién construye la dependencia. La prueba `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` es la evidencia de que la testabilidad es consecuencia directa de aplicar DIP.

### 4. Por qué H2 real pero gateway mockeado
H2 es un **Fake** aceptable: su contrato SQL/JPA es equivalente al de una base real para esta consulta, no requiere infraestructura y es rápido y determinista. Un proveedor de SMS de terceros no tiene un "H2 equivalente" gratuito; sustituirlo con un **Mock** evita depender de credenciales, de red y de la disponibilidad del proveedor en cada ejecución del pipeline. En `@SpringBootTest` también se reemplaza el `Clock` con un bean `@Primary` fijo para que la ventana de 24 h sea determinista.

### 5. Integración continua
`.github/workflows/ci.yml` ejecuta `mvn -B verify` en cada push y pull request a `main` (checkout, JDK 21 Temurin con caché de Maven) y publica el reporte JaCoCo como artefacto. No hay bloque `services:` con una base de datos porque las pruebas de integración usan H2 en memoria; con PostgreSQL + Testcontainers el runner necesitaría Docker y tendría tiempos de arranque mayores.

### 6. Dos detalles técnicos que importan
- **Inyección ambigua evitada.** Tras la Parte 2 existen tres beans `ReminderChannel` (`emailReminderChannel`, `smsReminderChannel`, `canalUrgente`). Por eso `ReminderChannelFactoryImpl` y el servicio usan `@Qualifier` por nombre de bean, en lugar de depender de la inyección por tipo.
- **Sobrescritura de beans.** En el `@TestConfiguration` el bean se llama `clockFijo` (no `clock`): Spring Boot desactiva por defecto la sobrescritura de beans, y reutilizar el nombre `clock` de `ClockConfig` haría fallar el contexto. `@Primary` garantiza que sea el que se inyecte.

## Evidencias
> Reemplazar por capturas reales antes de entregar.
- `docs/img/tests-verde.png`: salida de `mvn verify` con todas las pruebas en verde.
- `docs/img/jacoco.png`: reporte de cobertura (`target/site/jacoco/index.html`).
- `docs/img/actions-success.png`: pestaña *Actions* con el workflow en "Success".

## Herramientas utilizadas
Java 21, Spring Boot 3.3, Spring Data JPA, H2, Lombok, JUnit 5, Mockito, JaCoCo, Apache Maven, GitHub Actions, Git y GitHub.

## Conclusiones
Escribir pruebas útiles dependió menos de la herramienta que del diseño: Strategy y Factory Method convirtieron decisiones de negocio (qué canal, cuál estrategia) en interacciones explícitas que un mock puede verificar. El caso de `enviarRecordatorioUrgente` mostró que un solo `new` en la lógica de negocio impide aislar un escenario crítico, y que moverlo al composition root lo resuelve sin cambiar el comportamiento. Elegir el double correcto también es una decisión de diseño: mock cuando importa la interacción, fake cuando existe una implementación real y fiel (`Clock.fixed`, H2). Finalmente, automatizar todo en CI garantiza que esas pruebas se ejecuten en cada cambio, y usar H2 mantiene el pipeline simple y rápido.
