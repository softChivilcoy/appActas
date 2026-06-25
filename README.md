## Ecosistema de Gestión de Actas Digitales (Muni Chivilcoy)
📱 Descripción General (Módulo Android)
Aplicación móvil nativa desarrollada en Kotlin diseñada exclusivamente para el cuerpo de inspectores de la Municipalidad de Chivilcoy. La herramienta permite la confección, registro y digitalización en territorio de Actas de Constatación de Tránsito e Inspección General, reemplazando por completo el soporte en papel y automatizando el flujo de datos.

El sistema opera bajo un enfoque híbrido, permitiendo el labrado de actas de forma 100% offline (sin conectividad) para garantizar la continuidad operativa en la vía pública, resguardando la información de manera local mediante una base de datos robusta (Room). Una vez que el dispositivo detecta una conexión estable, un servicio en segundo plano (WorkManager) sincroniza los datos de manera asincrónica con el servidor central mediante una arquitectura Multipart.

## 🚀 Características Principales
Modularidad Funcional (Tránsito / Inspección): Interfaz dinámica que adapta los campos del formulario según la naturaleza del operativo (carga de patentes, datos de alcoholemia técnica, retención de licencias, o detalles de inspección comercial y edilicia).

Geolocalización Nativa: Captura automática de coordenadas GPS en el momento exacto del labrado para auditar la precisión del lugar del hecho.

Evidencia Fotográfica Premium (Optimizada): Captura fotográfica en alta resolución (2K) integrada nativamente con la cámara del dispositivo. Cuenta con un motor de compresión inteligente que procesa las imágenes en el almacenamiento temporal, reduciendo el peso a un estándar óptimo (~450KB - 650KB) sin perder nitidez en detalles críticos como patentes o textos de actas de descargo.

Firma Digital in Situ: Pantalla táctil integrada para la captura de la firma ológrafa digitalizada del infractor, testigos o del inspector interviniente.

Sincronización Inteligente Resiliente: El módulo de red procesa de forma integrada los datos estructurados (JSON) junto a los archivos binarios de las imágenes. Ante fallas temporales de señal, el sistema reintenta la operación de manera automática preservando la integridad de los datos.

## ⚖️ Conexión e Integración con el Juzgado de Faltas
El destino final de cada acta sincronizada es el Sistema Central de Gestión del Juzgado de Faltas.

Validación y Consistencia: Al impactar en el backend (Laravel), el acta recibe un ID único y correlativo del servidor, validando la consistencia atómica de todas sus tablas hijas (infractor, faltas seleccionadas, inventario de secuestro, testigos e imágenes).

Disponibilidad Inmediata: Al quedar impactada en la base de datos central, la causa se pre-constituye automáticamente en el panel administrativo del Juzgado.

Despacho y Celeridad: Los secretarios del juzgado y los jueces de faltas pueden visualizar de inmediato el acta digitalizada, las observaciones del inspector, el mapa con el GPS del hecho y las fotos adjuntas con nitidez premium. Esto elimina los tiempos muertos de transcripción manual del papel, acelera los tiempos de notificación y dota al proceso de una transparencia jurídica absoluta con evidencia digital irrefutable.


## Fase 1: Flujo de Trabajo del Inspector en el Teléfono

Selección del Módulo: El inspector inicia un operativo en la app y selecciona el tipo de acta a labrar: TRÁNSITO o INSPECCIÓN GENERAL.

Carga de Datos Comunes: Completa la localización (la calle se detecta o selecciona, la altura, y el GPS captura la latitud/longitud de forma nativa), datos del infractor (nombre, DNI, dirección) y las faltas constatadas desde el catálogo.

Bifurcación Dinámica según el Tipo:

Si es Tránsito: El inspector carga los datos del vehículo (patente, marca), realiza la alcoholemia técnica (si aplica, ingresando marca/número de serie del alcoholímetro) y tilda las medidas preventivas o inventario de secuestro.

Si es Inspección: Se despliega el formulario de procedimiento edilicio/comercial, datos de habilitación municipal del comercio o nomenclatura catastral completa (circunscripción, sección, manzana, parcela).

Captura de Evidencias: Accede a la pantalla de fotos. Al presionar "Sacar Foto", la cámara nativa se abre mediante un archivo temporal real en el disco. Al aceptar, la app renderiza la miniatura en el GridLayout de 3 columnas sin saturar la memoria RAM.

Cierre y Firma: Se capturan las firmas ológrafas digitales en pantalla y el inspector presiona "Finalizar Acta".

## Fase 2: El Modelo de Persistencia Local (Room)
Cuando el inspector finaliza el acta en el teléfono, la aplicación no la envía inmediatamente a Internet para garantizar la resiliencia en zonas sin señal. En su lugar, el sistema ejecuta una transacción de guardado local utilizando el patrón DAO en Room.


##  📡 Fase 3: Proceso Interno de Sincronización Asincrónica (Multipart)
Una vez que el dispositivo cuenta con acceso a datos móviles o Wi-Fi, el sistema operativo despierta al componente SincronizacionWorker mediante el servicio de WorkManager

## 🏛️ Fase 4: Recepción, Impacto en Backend (Laravel) y Juzgado
Al llegar la petición HTTP a la ruta /actas en Laravel (api.php), se procesa de forma transaccional para asegurar consistencia absoluta:

## 🏁 Fase 5: Cierre del Circuito y Limpieza Automatizada
Al recibir el código exitoso 201 en el teléfono, el componente SincronizacionWorker toma el control final:

Confirmación en Logs: El sistema registra: Acta local #X y sus imágenes binarias sincronizadas con éxito.

Ejecución del Borrado en Cascada local: Llama al método del DAO eliminarActaLocalCompleta(idLocal).

Liberación de Memoria: Al eliminar el registro padre de la tabla actas en Room, el motor SQLite local de Android borra en cascada instantáneamente todas las filas asociadas en las 11 tablas hijas de ese ID local específico.

De esta forma, la base de datos interna del teléfono se mantiene siempre liviana, limpia de transacciones viejas, y lista para el próximo operativo del inspector en Chivilcoy


## 🗺️ DIAGRAMA DE FLUJO: Del territorio al Juzgado

```text
       [ INSPECTOR EN LA CALLE ]
                  │
                  ▼
   ┌──────────────────────────────┐
   │ 1. Labrado de Acta en la App │ ──► (Carga rápida de datos y firmas)
   └──────────────────────────────┘
                  │
                  ▼
   ┌──────────────────────────────┐
   │ 2. Captura Fotográfica Real  │ ──► (Fotos 2K optimizadas: alta nitidez, bajo peso)
   └──────────────────────────────┘
                  │
                  ▼
   ┌──────────────────────────────┐
   │  3. Guarda en Memoria Local  │ ──► (100% OFFLINE: Funciona sin señal ni datos)
   └──────────────────────────────┘
                  │
         ┌────────┴────────┐
         ▼                 ▼
   ¿Hay Señal? ──NO──► [ Espera en cola segura dentro del teléfono ]
         │
         SI
         ▼
   ┌──────────────────────────────┐
   │ 4. Sincronización Automática │ ──► (El teléfono sube datos y fotos en segundo plano)
   └──────────────────────────────┘
                  │
                  ▼
   ┌──────────────────────────────┐
   │ 5. Recepción y Resguardo Srv │ ──► (El servidor central blinda y procesa el acta)
   └──────────────────────────────┘
                  │
                  ▼
      [ JUZGADO DE FALTAS WEB ]    ──► ¡Causa lista para el Juez con fotos y mapa!

```
## 🗄️ DIAGRAMA RELACIONAL: Estructura de la Base de Datos Central

```text
                     ┌─────────────────────────────┐
                     │           ACTAS             │  ◄─── (Tabla Principal)
                     ├─────────────────────────────┤
                     │ PK │ id (idInsertado)       │
                     │    │ nro_acta               │
                     │    │ tipoacta (1=TR, 2=IN)  │
                     │    │ fecha / hora           │
                     │    │ nombrecalle / alturacalle
                     │    │ latitud / longitud     │
                     │    │ idinspector / idjuzgado│
                     │    │ estado ("Creada")      │
                     └──────────────┬──────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         │ (1 a 1)                  │ (1 a Muchos)             │ (1 a Muchos)
         ▼                          ▼                          ▼
┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│  ACTA_INFRACTOR  │       │   ACTA_FALTAS    │       │   ACTA_TESTIGOS  │
├──────────────────┤       ├──────────────────┤       ├──────────────────┤
│ FK │ actaid      │       │ FK │ actaid      │       │ FK │ actaid      │
│    │ nombrecompl │       │    │ codigofalta │       │    │ dnioriginal │
│    │ dni         │       │    │ descripcion │       │    │ nombreorig  │
│    │ provincia   │       └──────────────────┘       │    │ domicilioor │
│    │ localidad   │                                  │    │ estadoverif │
│    │ calle/altura│                                  └──────────────────┘
└──────────────────┘

 ════════════════════════════ SIMBOLOGÍA ════════════════════════════════
   ▲
   │  (Saturación según el Tipo de Acta que elija el Inspector)
   │
   ├─► [ SI EL TIPO DE ACTA ES: "TRANSITO" (1) ]
   │   │
   │   ├──► ┌──────────────────┐ (1 a 1)
   │   │    │  ACTA_VEHICULO   │ ──► [dominio, marca, modelo, tipovehiculo]
   │   │    └──────────────────┘
   │   ├──► ┌──────────────────┐ (1 a 1)
   │   │    │ ACTA_ALCOHOLEMIAS│ ──► [resultadoalcoholemia, marca/modelo, nroserie]
   │   │    └──────────────────┘
   │   ├──► ┌──────────────────┐ (1 a 1)
   │   │    │ACTA_MEDPREVENTIVAS
   │   │    └──────────────────┘ ──► [realizoalcoholemia, retencionvehiculo/licencia]
   │   └──► ┌──────────────────┐ (1 a 1)
   │        │  ACTA_SECUESTRO  │ ──► [numeromotor/chasis, inventarioserializado]
   │        └──────────────────┘
   │
   └─► [ SI EL TIPO DE ACTA ES: "INSPECCION" (2) ]
       │
       └──► ┌──────────────────┐ (1 a 1)
            │ACTA_PROCEDIMIENTO│ ──► [tipoinspeccion, tipoinmueble, seprocedea]
            └──────────────────┘

 ═════════════════════ GESTIÓN DE ARCHIVOS FÍSICOS ═══════════════════════
   │
   └──► ┌──────────────────┐ (1 a Muchos)
        │    ACTA_FOTOS    │
        ├──────────────────┤
        │ FK │ actaid      │
        │    │ tipomedia   │
        │    │ rutaarchivo │ ──► (Apunta a: "storage/actas/acta_X_uniqid.jpg")
        └──────────────────┘
