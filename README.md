Ecosistema de Gestión de Actas Digitales (Muni Chivilcoy)
📱 Descripción General (Módulo Android)
Aplicación móvil nativa desarrollada en Kotlin diseñada exclusivamente para el cuerpo de inspectores de la Municipalidad de Chivilcoy. La herramienta permite la confección, registro y digitalización en territorio de Actas de Constatación de Tránsito e Inspección General, reemplazando por completo el soporte en papel y automatizando el flujo de datos.

El sistema opera bajo un enfoque híbrido, permitiendo el labrado de actas de forma 100% offline (sin conectividad) para garantizar la continuidad operativa en la vía pública, resguardando la información de manera local mediante una base de datos robusta (Room). Una vez que el dispositivo detecta una conexión estable, un servicio en segundo plano (WorkManager) sincroniza los datos de manera asincrónica con el servidor central mediante una arquitectura Multipart.

🚀 Características Principales
Modularidad Funcional (Tránsito / Inspección): Interfaz dinámica que adapta los campos del formulario según la naturaleza del operativo (carga de patentes, datos de alcoholemia técnica, retención de licencias, o detalles de inspección comercial y edilicia).

Geolocalización Nativa: Captura automática de coordenadas GPS en el momento exacto del labrado para auditar la precisión del lugar del hecho.

Evidencia Fotográfica Premium (Optimizada): Captura fotográfica en alta resolución (2K) integrada nativamente con la cámara del dispositivo. Cuenta con un motor de compresión inteligente que procesa las imágenes en el almacenamiento temporal, reduciendo el peso a un estándar óptimo (~450KB - 650KB) sin perder nitidez en detalles críticos como patentes o textos de actas de descargo.

Firma Digital in Situ: Pantalla táctil integrada para la captura de la firma ológrafa digitalizada del infractor, testigos o del inspector interviniente.

Sincronización Inteligente Resiliente: El módulo de red procesa de forma integrada los datos estructurados (JSON) junto a los archivos binarios de las imágenes. Ante fallas temporales de señal, el sistema reintenta la operación de manera automática preservando la integridad de los datos.

⚖️ Conexión e Integración con el Juzgado de Faltas
El destino final de cada acta sincronizada es el Sistema Central de Gestión del Juzgado de Faltas.

Validación y Consistencia: Al impactar en el backend (Laravel), el acta recibe un ID único y correlativo del servidor, validando la consistencia atómica de todas sus tablas hijas (infractor, faltas seleccionadas, inventario de secuestro, testigos e imágenes).

Disponibilidad Inmediata: Al quedar impactada en la base de datos central, la causa se pre-constituye automáticamente en el panel administrativo del Juzgado.

Despacho y Celeridad: Los secretarios del juzgado y los jueces de faltas pueden visualizar de inmediato el acta digitalizada, las observaciones del inspector, el mapa con el GPS del hecho y las fotos adjuntas con nitidez premium. Esto elimina los tiempos muertos de transcripción manual del papel, acelera los tiempos de notificación y dota al proceso de una transparencia jurídica absoluta con evidencia digital irrefutable.

¿Qué te parece? Si necesitás agregarle algún detalle técnico específico (como las tecnologías del backend o las tablas involucradas), me avisás y lo sumamos.
