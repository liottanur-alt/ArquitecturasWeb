# API Gateway

Recibe solicitudes en http://localhost:8080, valida el JWT y las reenvía al servicio correspondiente. No tiene base propia.

| Rutas, incluidas sus subrutas | Servicio local |
| --- | --- |
| /auth, /auth/**, /usuarios, /roles | Usuarios: 8004 |
| /api/eventos, /api/lugares, /api/artistas | Eventos: 8005 |
| /sectores, /asientos, /reservas, /eventos/{id}/inventario, /eventos/{id}/sectores | SectorAsiento: 8002 |
| /compras, /entradas, /solicitudes | Compras: 8003 |

## Autenticación

El Gateway usa el mismo HS256, secreto e issuer que Usuarios:

- `JWT_SECRET`: obligatorio, al menos 32 bytes. Compose lo toma de `.env`; no hay valor por defecto.
- Issuer esperado: `msv-usuario`.
- El claim `roles` se convierte en autoridades `ROLE_*` y el claim `sub` es la identidad del usuario.

En Docker, los destinos se configuran con MSVC_USUARIOS_URL, MSVC_EVENTOS_URL, MSVC_SECTORES_URL y MSVC_COMPRAS_URL; Compose los define automáticamente. Para ejecutar desde esta carpeta con los servicios ya disponibles:

```powershell
./mvnw.cmd spring-boot:run
```

En Linux/macOS usar `sh ./mvnw spring-boot:run`. Fuera de Compose deben definirse `JWT_SECRET` y las URLs de los servicios.

## Política de autorización

| Solicitudes | Acceso |
| --- | --- |
| POST /auth/registro, POST /auth/login, GET /actuator/health | Público. |
| Catálogos de lectura, sectores, inventario, /compras/**, /entradas/**, /solicitudes/** | Cualquier token válido, con las restricciones específicas siguientes. |
| /roles/**, GET y POST /usuarios, PUT /solicitudes/{id}/resolver | Solo ADMINISTRADOR. |
| /usuarios/{id} y sus subrutas | El dueño indicado por `sub` del JWT, o ADMINISTRADOR. |
| POST /api/eventos, PATCH /api/eventos/{id}/publicar, GET /api/eventos/mis-eventos | ORGANIZADOR o ADMINISTRADOR. |
| GET /entradas/qr/** | PERSONAL_ACCESO o ADMINISTRADOR. |
| Cualquier ruta con el segmento `interno`, y el resto de /actuator/** | Bloqueado. |

El Gateway reemplaza `X-Organizador-Id` con el `sub` verificado del JWT, por lo que una cabecera enviada por el cliente no puede suplantar al organizador. El resto de /actuator no se expone.

El registro público crea solo CLIENTE; la asignación de roles requiere ADMINISTRADOR. Los microservicios aguas abajo validan el mismo JWT localmente, sobrescriben la identidad recibida en el cuerpo con el `sub` autenticado y solo aceptan JWT de servicio con `ROLE_SERVICIO_INTERNO` en sus rutas internas. El Gateway sigue bloqueando esas rutas antes de enrutar.

## Pruebas

`./mvnw.cmd test` ejecuta 21 pruebas con proveedores HTTP simulados y JWT reales generados en el perfil `test` (secreto exclusivo de pruebas, reemplazable mediante `JWT_SECRET`). Cubren arranque, rutas existentes, tokens inválidos/vencidos, roles, propiedad de recursos, rutas internas y sobrescritura de la identidad. Las [pruebas de sistema](../tests/system/README.md) usan los servicios reales.
