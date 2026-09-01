import DAO.ClienteDAO;
import DAO.FacturaDAO;
import DAO.FacturaProductoDAO;
import DAO.ProductoDAO;
import Repository.mysql.MySQLClienteDAO;
import Repository.mysql.MySQLDAOFactory;
import factory.DAOFactory;
import factory.DBType;

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
        DAOFactory factory = DAOFactory.getInstance(DBType.MYSQL);

        // 2. Creas los DAOs a través de la interfaz abstracta (no usas la clase concreta directamente)
        ClienteDAO cliente = factory.createClienteDAO();
        FacturaDAO factura = factory.createFacturaDAO();
        ProductoDAO producto = factory.createProductoDAO();
        FacturaProductoDAO fpd = factory.createFacturaProductoDAO();

        // 3. Cargas de datos desde los CSV
        System.out.println("Insertar Datos desde CSV");
        cliente.insertarDatosCsv();
        factura.insertarDatosCsv();
        producto.insertarDatosCsv();
        fpd.insertarDatosCsv();

        // Lectura y Mostrado de Datos
        System.out.println("Mostrar datos cargados");
        System.out.println("Datos Cliente");
        System.out.println(cliente.buscarTodo());
        System.out.println("-------------------------------------");
        System.out.println("Mostrar datos factura");
        System.out.println(factura.buscarTodas());
        System.out.println("-------------------------------------");
        System.out.println("Mostrar datos producto");
        System.out.println(producto.getProductos());
        System.out.println("-------------------------------------");
        System.out.println("Mostrar datos factura producto");
        System.out.println(fpd.getFacturasProductos());
        System.out.println("-------------------------------------");
        System.out.println("Datos del ej 3 ( Producto que mas recaudo )");
        System.out.println(producto.getRecaudacion());
        System.out.println("-------------------------------------");
        System.out.println("Datos del ej 4 ( Top 5 de clientes a los que mas se le facturó )");
        System.out.println(cliente.getClientesByMayorFacturacion());


    }
}