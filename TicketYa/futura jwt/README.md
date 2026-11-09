# JWT para una etapa futura

Esta carpeta conserva la implementacion JWT retirada del codigo activo a pedido del usuario. Esta fuera de los src/main y src/test de los proyectos Maven: no se compila ni se registra como componente Spring.

Se movieron ocho archivos exclusivos: SecurityConfig, UsuarioAccess, AuthService, AuthController, LoginRequest, RegistroRequest, TokenResponse y AuthIntegrationTests. Se preservaron tambien las versiones originales de dieciocho archivos compartidos, incluyendo pom, entidad Usuario, controlador, inicializacion, propiedades y documentacion de Usuarios, rutas y pruebas del gateway, y la guia anterior. Las rutas relativas originales se conservan.

manifest.json indica para cada archivo si fue movido o copiado y el SHA-256 original. Las copias de entidades/repositorios/DTOs compartidos no significan que esos componentes deban quitarse de la version activa: siguen siendo necesarios para el dominio.

## Estado activo

Usuarios, roles y favoritos funcionan sin autenticacion. Se crean perfiles con POST /usuarios y se consultan roles con GET /roles. No hay /auth/login ni /auth/registro. Los servicios de Eventos y SectorAsiento no se cambian por esta separacion. La columna password_hash puede seguir en una base existente: Hibernate update no la elimina y no se ejecuta ningun SQL para borrar hashes ni usuarios.

## Restauracion futura

Usar esta carpeta como referencia y comparar archivos antes de restaurar. Los archivos compartidos son una fotografia y podrian quedar desactualizados frente a cambios futuros; no copiar todo indiscriminadamente.

1. Reincorporar las dependencias de Spring Security/Resource Server del pom archivado.
2. Restaurar los archivos exclusivos en sus rutas originales.
3. Reintegrar passwordHash en Usuario, PasswordEncoder en DatosIniciales, propiedades security.jwt y reglas @PreAuthorize del controlador, revisando la logica activa antes de sobrescribir.
4. Reincorporar las rutas /auth del gateway y sus pruebas.
5. Restaurar/adaptar AuthIntegrationTests y actualizar las pruebas sin autenticacion para la nueva politica.
6. Ejecutar las suites de Usuarios y Gateway y verificar el flujo completo con el resto de servicios.

La configuracion y claves de ejemplo archivadas corresponden al entorno local. La carpeta no es un microservicio independiente ni incluye un proyecto compilable completo. No contiene bases de datos ni archivos target generados.
