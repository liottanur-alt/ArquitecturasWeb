# Arquitectura de Microservicios

En esta carpeta se desarrollarán los microservicios del sistema de gestión de eventos y venta de entradas.

## Reglas generales

- Cada microservicio debe llamarse `msv-{nombre}`.
- Cada microservicio debe tener sus propias entidades, repositorios, servicios y controladores REST.
- Los microservicios deben ser independientes.
- La comunicación entre microservicios debe realizarse mediante APIs.
- El `api-gateway` será el punto de entrada al sistema.

## Estructura del proyecto

```text
microservicios/
├── api-gateway/
├── msv-eventos/
├── msv-sectores-asientos/
├── msv-compras/
└── msv-usuarios/
```

## Responsabilidades

### 1. msv-eventos
Gestionar los eventos, lugares y artistas.

**Entidades:** `Evento`, `Lugar`, `Artista`

### 2. msv-sectores-asientos
Gestionar los sectores, asientos y su disponibilidad.

**Entidades:** `Sector`, `Asiento`

### 3. msv-compras
Gestionar las solicitudes, compras y entradas.

**Entidades:** `Compra`, `Entrada`, `Solicitud`

### 4. msv-usuarios
Gestionar los usuarios y sus roles.

**Entidades:** `Usuario`, `Rol`

## API Gateway

Centralizar las peticiones HTTP y redirigirlas al microservicio correspondiente.
