-- Seleccionar la base de datos creada en el docker-compose
CREATE DATABASE IF NOT EXISTS integrador1;
USE integrador1;

-- 1. Tabla Cliente
CREATE TABLE IF NOT EXISTS Cliente (
                                       idCliente INT PRIMARY KEY,
                                       nombre VARCHAR(500),
    email VARCHAR(150)
    );

-- 2. Tabla Factura (Relacionada con Cliente)
CREATE TABLE IF NOT EXISTS Factura (
                                       idFactura INT PRIMARY KEY,
                                       idCliente INT,
                                       CONSTRAINT fk_factura_cliente
                                       FOREIGN KEY (idCliente) REFERENCES Cliente(idCliente)
    ON DELETE CASCADE ON UPDATE CASCADE
    );

-- 3. Tabla Producto
CREATE TABLE IF NOT EXISTS Producto (
                                        idProducto INT PRIMARY KEY,
                                        nombre VARCHAR(45),
    valor FLOAT
    );

-- 4. Tabla Intermedia Factura_Producto (Relación M:N)
CREATE TABLE IF NOT EXISTS Factura_Producto (
                                                idFactura INT,
                                                idProducto INT,
                                                cantidad INT,
                                                PRIMARY KEY (idFactura, idProducto),
    CONSTRAINT fk_fp_factura
    FOREIGN KEY (idFactura) REFERENCES Factura(idFactura)
    ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_fp_producto
    FOREIGN KEY (idProducto) REFERENCES Producto(idProducto)
    ON DELETE CASCADE ON UPDATE CASCADE
    );