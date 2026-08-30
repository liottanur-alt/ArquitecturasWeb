public class Main {
    public static void main(String[] args) {
        /* =================================================================================
         * GUÍA RÁPIDA DE RESOLUCIÓN - TRABAJO PRÁCTICO INTEGRADOR 1
         * =================================================================================
         *
         * 1. CONEXIÓN A LA DB Y CREACIÓN DE LA FÁBRICA:
         *    Se obtiene la conexión (MySQL/PostgreSQL) y se crea el DAOFactory para gestionar el acceso a los datos.
         *
         * 2. INSTANCIACIÓN DE LOS DAOs:
         *    Se obtienen los DAOs de cada entidad (Cliente, Factura, Producto, FacturaProducto) mediante la fábrica.
         *
         * 3. CARGA DE DATOS DESDE CSV:
         *    Se leen los archivos CSV y se insertan los datos respetando las claves foráneas (Clientes/Productos -> Facturas -> FacturaProducto).
         *
         * 4. LECTURA Y MOSTRADO DE DATOS:
         *    Se ejecutan los métodos de consulta general (`Select`) para verificar que la información se cargó correctamente.
         *
         * 5. RESOLUCIÓN DE LOS EJERCICIOS DE NEGOCIO:
         *    - Ej. 3: Consulta SQL (`JOIN` + `SUM`) para obtener el producto que más recaudó.
         *    - Ej. 4: Consulta SQL (`JOIN` + `GROUP BY` + `LIMIT 5`) para obtener los 5 clientes con mayor facturación.
         * =================================================================================
         */
    }
}