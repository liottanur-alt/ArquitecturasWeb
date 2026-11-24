@{
    Lugares = @{
        Arena = @{ nombre = 'Movistar Arena'; direccion = 'Humboldt 450'; ciudad = 'Buenos Aires'; tipo = 'ESTADIO'; latitud = -34.5945; longitud = -58.4486 }
        SanMartin = @{ nombre = 'Teatro General San Martín'; direccion = 'Av. Corrientes 1530'; ciudad = 'Buenos Aires'; tipo = 'TEATRO'; latitud = -34.6041; longitud = -58.3891 }
        Libertad = @{ nombre = 'Palacio Libertad'; direccion = 'Sarmiento 151'; ciudad = 'Buenos Aires'; tipo = 'SALA'; latitud = -34.6030; longitud = -58.3693 }
        Konex = @{ nombre = 'Ciudad Cultural Konex'; direccion = 'Sarmiento 3131'; ciudad = 'Buenos Aires'; tipo = 'SALA'; latitud = -34.6062; longitud = -58.4097 }
        Usina = @{ nombre = 'Usina del Arte'; direccion = 'Agustín Caffarena 1'; ciudad = 'Buenos Aires'; tipo = 'SALA'; latitud = -34.6289; longitud = -58.3569 }
        Recoleta = @{ nombre = 'Centro Cultural Recoleta'; direccion = 'Junín 1930'; ciudad = 'Buenos Aires'; tipo = 'SALA'; latitud = -34.5877; longitud = -58.3916 }
        Circulo = @{ nombre = 'Teatro El Círculo'; direccion = 'Laprida 1223'; ciudad = 'Rosario'; tipo = 'TEATRO'; latitud = -32.9516; longitud = -60.6376 }
        Nave = @{ nombre = 'Nave Cultural'; direccion = 'Av. España y Juan Agustín Maza'; ciudad = 'Mendoza'; tipo = 'SALA'; latitud = -32.8747; longitud = -68.8411 }
        Estadio = @{ nombre = 'Estadio Único Madre de Ciudades'; direccion = 'Costanera del Río Dulce, sector norte'; ciudad = 'Santiago del Estero'; tipo = 'ESTADIO'; latitud = -27.7663; longitud = -64.2439 }
    }
    Eventos = @(
        @{
            nombre = 'Las Luces del Sur en vivo'; tipo = 'RECITAL'; lugar = 'Arena'; participante = 'Las Luces del Sur'; genero = 'Rock alternativo'
            descripcion = 'Guitarras expansivas, estribillos para cantar de pie y un estreno en vivo que cierra la gira del grupo por el país.'
            semanas = 3; dia = 'Saturday'; hora = 21; precio = 58000; capacidad = 480; preventa = 0; demanda = $true
        }
        @{
            nombre = 'La casa de las mareas'; tipo = 'TEATRO'; lugar = 'SanMartin'; participante = 'Compañía Umbral'; genero = 'Drama'
            descripcion = 'Dos hermanas vuelven a la casa familiar y encuentran cartas que cambian el sentido de sus recuerdos. Una obra íntima con música en escena.'
            semanas = 3; dia = 'Sunday'; hora = 20; precio = 26000; capacidad = 10; preventa = 9
        }
        @{
            nombre = 'Sur DevConf'; tipo = 'CONFERENCIA'; lugar = 'Libertad'; participante = 'Comunidad Sur Dev'; genero = 'Tecnología'
            descripcion = 'Una jornada de charlas y talleres sobre arquitectura web, accesibilidad y herramientas de desarrollo, con espacio para compartir proyectos.'
            semanas = 4; dia = 'Friday'; hora = 9; precio = 32000; capacidad = 180; preventa = 0
        }
        @{
            nombre = 'Copa Federal Sub-20'; tipo = 'DEPORTE'; lugar = 'Estadio'; participante = 'Circuito Federal Juvenil'; genero = 'Fútbol'
            descripcion = 'Los mejores equipos juveniles del circuito disputan dos semifinales y la final en una jornada de fútbol para alentar en familia.'
            semanas = 4; dia = 'Sunday'; hora = 16; precio = 18500; capacidad = 650; preventa = 0
        }
        @{
            nombre = 'Patio Eléctrico'; tipo = 'FESTIVAL'; lugar = 'Konex'; participante = 'Colectivo Frecuencia Patio'; genero = 'Electrónica'
            descripcion = 'Una tarde de sintetizadores en vivo, instalaciones lumínicas y sets que llevan el baile al patio hasta la noche.'
            semanas = 5; dia = 'Saturday'; hora = 17; precio = 39000; capacidad = 10; preventa = 10
        }
        @{
            nombre = 'Piano al borde del río'; tipo = 'RECITAL'; lugar = 'Usina'; participante = 'Julia del Monte Trío'; genero = 'Jazz'
            descripcion = 'Piano, contrabajo y batería se encuentran con la música del litoral en un concierto de composiciones propias e improvisación.'
            semanas = 6; dia = 'Friday'; hora = 20; precio = 24500; capacidad = 120; preventa = 0
        }
        @{
            nombre = 'El último tren a Rosario'; tipo = 'TEATRO'; lugar = 'Circulo'; participante = 'Elenco La Estación'; genero = 'Comedia dramática'
            descripcion = 'Una demora nocturna reúne a tres desconocidos en el andén. Entre malentendidos y confesiones, cada uno encuentra una razón inesperada para quedarse.'
            semanas = 6; dia = 'Saturday'; hora = 20; precio = 22000; capacidad = 10; preventa = 10
        }
        @{
            nombre = 'Laboratorio de fotografía urbana'; tipo = 'OTRO'; lugar = 'Recoleta'; participante = 'Colectivo Lente Abierto'; genero = 'Fotografía'
            descripcion = 'Ejercicios de encuadre y luz natural, una salida breve por el barrio y una puesta en común de las imágenes. Se puede participar con celular.'
            semanas = 7; dia = 'Saturday'; hora = 11; precio = 12500; capacidad = 10; preventa = 9
        }
        @{
            nombre = 'Innovación de montaña'; tipo = 'CONFERENCIA'; lugar = 'Nave'; participante = 'Red Productiva Cuyana'; genero = 'Tecnología y ambiente'
            descripcion = 'Equipos de Cuyo presentan proyectos de riego inteligente y sensores ambientales en una jornada con demostraciones y talleres abiertos.'
            semanas = 8; dia = 'Friday'; hora = 10; precio = 20000; capacidad = 160; preventa = 0
        }
        @{
            nombre = 'Vinilos y cocina de estación'; tipo = 'FESTIVAL'; lugar = 'Nave'; participante = 'Feria Surco'; genero = 'Gastronomía y música'
            descripcion = 'Cocineros mendocinos reúnen platos de estación en una feria que acompaña el encuentro con sesiones de vinilo y puestos de sellos independientes.'
            semanas = 9; dia = 'Sunday'; hora = 13; precio = 16000; capacidad = 240; preventa = 0
        }
        @{
            nombre = 'Medianoche de cuerdas'; tipo = 'RECITAL'; lugar = 'Usina'; participante = 'Cuarteto Bruma'; genero = 'Música de cámara'
            descripcion = 'Un concierto nocturno de cuerdas con arreglos de tango contemporáneo y piezas del repertorio rioplatense.'
            semanas = 0; dia = 'Friday'; hora = 23; hoy = $true; precio = 14500; capacidad = 90; preventa = 0
        }
        @{
            nombre = 'Feria de editoriales del río'; tipo = 'OTRO'; lugar = 'Recoleta'; participante = 'Mesa Editorial del Río'; genero = 'Literatura'
            descripcion = 'Editoriales independientes presentan novedades, fanzines y libros ilustrados. Habrá lecturas y conversaciones abiertas.'
            semanas = 0; dia = 'Saturday'; hora = 15; precio = 0; capacidad = 180; preventa = 0
        }
        @{
            nombre = 'La tregua del domingo'; tipo = 'TEATRO'; lugar = 'SanMartin'; participante = 'Compañía Tercer Acto'; genero = 'Comedia'
            descripcion = 'Una sobremesa familiar se desordena cuando un invitado inesperado anuncia que no piensa irse.'
            semanas = 4; dia = 'Saturday'; hora = 20; precio = 28500; capacidad = 18; filas = @('A','B','C'); columnas = 6; preventa = 5; casosEntradas = $true
        }
        @{
            nombre = 'Horizonte en vivo'; tipo = 'RECITAL'; lugar = 'Arena'; participante = 'Horizonte Polar'; genero = 'Indie pop'
            descripcion = 'El grupo presenta un disco de canciones nocturnas con una puesta de luces envolvente y músicos invitados.'
            semanas = 5; dia = 'Friday'; hora = 21; precio = 62000; capacidad = 16; filas = @('A','B'); columnas = 8; preventa = 0; demanda = $true
        }
        @{
            nombre = 'Foro de diseño accesible'; tipo = 'CONFERENCIA'; lugar = 'Libertad'; participante = 'Red Diseño Abierto'; genero = 'Diseño'
            descripcion = 'Equipos de diseño y desarrollo comparten experiencias sobre accesibilidad en productos, servicios y espacios culturales.'
            semanas = 6; dia = 'Thursday'; hora = 10; precio = 18500; capacidad = 120; preventa = 0; destino = 'BORRADOR'
        }
        @{
            nombre = 'Noche de son y percusión'; tipo = 'FESTIVAL'; lugar = 'Konex'; participante = 'Círculo Percusivo'; genero = 'Música latina'
            descripcion = 'Conjuntos de son, timbales y salsa se alternan en una jornada con pista de baile y talleres para principiantes.'
            semanas = 7; dia = 'Saturday'; hora = 18; precio = 24000; capacidad = 125; preventa = 0; destino = 'SUSPENDIDO'
        }
        @{
            nombre = 'Encuentro de orquestas del litoral'; tipo = 'RECITAL'; lugar = 'Circulo'; participante = 'Orquesta Puente Paraná'; genero = 'Música sinfónica'
            descripcion = 'Ensamblajes de la región comparten obras latinoamericanas y un cierre coral en el teatro rosarino.'
            semanas = 8; dia = 'Sunday'; hora = 19; precio = 31000; capacidad = 80; preventa = 2; destino = 'CANCELADO'
        }
        @{
            nombre = 'Copa de invierno de futsal'; tipo = 'DEPORTE'; lugar = 'Estadio'; participante = 'Liga Federal de Salón'; genero = 'Futsal'
            descripcion = 'Tres partidos en una jornada de fútbol sala, con apertura de puertas una hora antes del primer encuentro.'
            semanas = 10; dia = 'Saturday'; hora = 17; precio = 15500; capacidad = 200; preventa = 0
        }
    )
}
