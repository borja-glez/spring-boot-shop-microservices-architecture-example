-- Demo catalog. Names are Spanish on purpose: accents, ñ, uppercase variants and the special
-- characters %, _ and İ exercise the search paths of specification-repository.

insert into seller (id, display_name, email, city) values
    ('seller-ana',    'Tostadores Ana',      'ana@tostadores.example',     'Madrid'),
    ('seller-bruno',  'Almazara Bruno',      'bruno@almazara.example',     'Jaén'),
    ('seller-carmen', 'Carmen Hogar',        'carmen@hogar.example',       'Valencia'),
    ('seller-diego',  'Diego Electrónica',   'diego@electronica.example',  'Bilbao'),
    ('seller-elena',  'Librería Elena',      'elena@libreria.example',     'A Coruña'),
    ('seller-fermin', 'Ultramarinos Fermín', 'fermin@ultramarinos.example','Zaragoza');

insert into category (id, slug, name) values
    (gen_random_uuid(), 'cafe-e-infusiones',  'Café e infusiones'),
    (gen_random_uuid(), 'despensa',           'Despensa'),
    (gen_random_uuid(), 'aceites-y-vinagres', 'Aceites y vinagres'),
    (gen_random_uuid(), 'conservas',          'Conservas'),
    (gen_random_uuid(), 'embutidos-y-quesos', 'Embutidos y quesos'),
    (gen_random_uuid(), 'vinos',              'Vinos'),
    (gen_random_uuid(), 'dulces',             'Dulces y repostería'),
    (gen_random_uuid(), 'hogar',              'Hogar'),
    (gen_random_uuid(), 'textil',             'Textil'),
    (gen_random_uuid(), 'electronica',        'Electrónica'),
    (gen_random_uuid(), 'ninos',              'Niños'),
    (gen_random_uuid(), 'libros',             'Libros');

with seed (sku, name, description, price, status, seller_id, categories, tags, days_ago) as (
    values
    ('CAF-001', 'Café de Colombia en grano 1 kg', 'Tueste medio, notas de caramelo y cítricos.', 18.90::numeric, 'ACTIVE', 'seller-ana', array['cafe-e-infusiones']::text[], array['ecologico','comercio-justo']::text[], 40::int),
    ('CAF-002', 'CAFÉ MOLIDO NATURAL 500 g', 'Molienda media para cafetera italiana.', 7.45, 'ACTIVE', 'seller-ana', array['cafe-e-infusiones'], array['molido'], 35),
    ('CAF-003', 'Cafetera italiana de aluminio 6 tazas', 'Clásica, apta para gas y vitrocerámica.', 24.00, 'ACTIVE', 'seller-carmen', array['cafe-e-infusiones','hogar'], array['cocina'], 60),
    ('CAF-004', 'Café descafeinado de Etiopía', 'Proceso suizo al agua, sin disolventes.', 12.80, 'ACTIVE', 'seller-ana', array['cafe-e-infusiones'], array['ecologico','descafeinado'], 12),
    ('CAF-005', 'Té verde matcha ceremonial 30 g', 'Cultivo japonés, molido en piedra.', 21.50, 'ACTIVE', 'seller-ana', array['cafe-e-infusiones'], array['ecologico','te'], 20),
    ('CAF-006', 'Infusión de manzanilla con anís', 'Veinte bolsitas biodegradables.', 3.20, 'ACTIVE', 'seller-fermin', array['cafe-e-infusiones'], array['te'], 90),
    ('CAF-007', 'Café de Guatemala Huehuetenango', 'Edición limitada, tueste claro.', 16.40, 'DRAFT', 'seller-ana', array['cafe-e-infusiones'], array['edicion-limitada'], 0),
    ('CAF-008', 'Rooibos con vainilla', 'Sin teína, ideal por la noche.', 5.90, 'DISCONTINUED', 'seller-fermin', array['cafe-e-infusiones'], array['te'], 200),
    ('DES-001', 'Arroz bomba D.O. Calasparra 1 kg', 'El arroz de las paellas de verdad.', 5.60, 'ACTIVE', 'seller-fermin', array['despensa'], array['denominacion-de-origen'], 50),
    ('DES-002', 'Garbanzos pedrosillanos 500 g', 'Pequeños y mantecosos.', 3.10, 'ACTIVE', 'seller-fermin', array['despensa'], array['legumbres'], 45),
    ('DES-003', 'Pimentón de la Vera picante', 'Ahumado con leña de encina.', 4.25, 'ACTIVE', 'seller-fermin', array['despensa'], array['especias','denominacion-de-origen'], 30),
    ('DES-004', 'Azafrán de La Mancha 1 g', 'Categoría coupé, D.O.P.', 9.95, 'ACTIVE', 'seller-fermin', array['despensa'], array['especias','denominacion-de-origen'], 25),
    ('DES-005', 'Lentejas pardinas ecológicas', 'Cultivadas en Tierra de Campos.', 2.95, 'ACTIVE', 'seller-fermin', array['despensa'], array['legumbres','ecologico'], 70),
    ('DES-006', 'Sal en escamas del Mediterráneo', 'Recogida a mano.', 4.80, 'ACTIVE', 'seller-bruno', array['despensa'], array[]::text[], 15),
    ('DES-007', 'Harina de trigo integral 1 kg', 'Molida a la piedra.', 2.40, 'DRAFT', 'seller-fermin', array['despensa'], array['ecologico'], 0),
    ('ACE-001', 'Aceite de oliva virgen extra picual 5 l', 'Cosecha temprana, frutado intenso.', 42.00, 'ACTIVE', 'seller-bruno', array['aceites-y-vinagres'], array['aove','denominacion-de-origen'], 55),
    ('ACE-002', 'Aceite de oliva virgen extra arbequina 500 ml', 'Suave y afrutado.', 9.90, 'ACTIVE', 'seller-bruno', array['aceites-y-vinagres'], array['aove'], 55),
    ('ACE-003', 'Vinagre de Jerez reserva', 'Envejecido en botas de roble.', 6.75, 'ACTIVE', 'seller-bruno', array['aceites-y-vinagres'], array['denominacion-de-origen'], 33),
    ('ACE-004', 'Aceite d''Oliva de l''Empordà', 'Variedad argudell, producción pequeña.', 14.30, 'ACTIVE', 'seller-bruno', array['aceites-y-vinagres'], array['aove','edicion-limitada'], 10),
    ('ACE-005', 'Aceite de girasol alto oleico 1 l', 'Para freír.', 3.60, 'DISCONTINUED', 'seller-bruno', array['aceites-y-vinagres'], array[]::text[], 300),
    ('CON-001', 'Bonito del norte en aceite de oliva', 'Pescado en temporada, Cantábrico.', 6.90, 'ACTIVE', 'seller-fermin', array['conservas'], array['pescado'], 28),
    ('CON-002', 'Mejillones en escabeche gallegos', 'Tamaño 8-12 piezas.', 4.70, 'ACTIVE', 'seller-elena', array['conservas'], array['pescado'], 22),
    ('CON-003', 'Pimientos del piquillo de Lodosa', 'Asados con leña.', 5.20, 'ACTIVE', 'seller-fermin', array['conservas'], array['denominacion-de-origen'], 26),
    ('CON-004', 'Anchoas del Cantábrico 00', 'Curación de 12 meses.', 15.90, 'ACTIVE', 'seller-fermin', array['conservas'], array['pescado','edicion-limitada'], 8),
    ('CON-005', 'Berberechos al natural', 'Rías gallegas.', 11.40, 'ACTIVE', 'seller-elena', array['conservas'], array['pescado'], 64),
    ('EMB-001', 'Jamón ibérico de bellota 100 g loncheado', 'Cortado a cuchillo.', 19.50, 'ACTIVE', 'seller-fermin', array['embutidos-y-quesos'], array['iberico'], 18),
    ('EMB-002', 'Queso manchego curado', 'Leche cruda de oveja, 12 meses.', 16.80, 'ACTIVE', 'seller-fermin', array['embutidos-y-quesos'], array['denominacion-de-origen'], 42),
    ('EMB-003', 'Chorizo de León dulce', 'Curado al humo.', 7.30, 'ACTIVE', 'seller-fermin', array['embutidos-y-quesos'], array[]::text[], 38),
    ('EMB-004', 'Queso de tetilla gallego', 'Cremoso y suave.', 9.40, 'ACTIVE', 'seller-elena', array['embutidos-y-quesos'], array['denominacion-de-origen'], 14),
    ('EMB-005', 'Sobrasada de Mallorca', 'Cerdo negro mallorquín.', 8.60, 'DRAFT', 'seller-fermin', array['embutidos-y-quesos'], array[]::text[], 0),
    ('VIN-001', 'Vino tinto Ribera del Duero crianza', 'Tempranillo, 14 meses en barrica.', 14.95, 'ACTIVE', 'seller-fermin', array['vinos'], array['denominacion-de-origen'], 48),
    ('VIN-002', 'Albariño Rías Baixas', 'Fresco y floral.', 11.90, 'ACTIVE', 'seller-elena', array['vinos'], array['denominacion-de-origen'], 36),
    ('VIN-003', 'Cava brut nature reserva', 'Método tradicional.', 9.50, 'ACTIVE', 'seller-carmen', array['vinos'], array[]::text[], 19),
    ('VIN-004', 'Vermú rojo de grifo 1 l', 'Receta de 1920.', 8.20, 'ACTIVE', 'seller-carmen', array['vinos'], array['edicion-limitada'], 7),
    ('VIN-005', 'Fino de Jerez en rama', 'Saca de primavera.', 12.60, 'DISCONTINUED', 'seller-bruno', array['vinos'], array['denominacion-de-origen'], 400),
    ('DUL-001', 'Turrón de Jijona tradicional', 'Almendra marcona 64 %.', 7.95, 'ACTIVE', 'seller-carmen', array['dulces'], array['navidad'], 5),
    ('DUL-002', 'Polvorones de Estepa', 'Caja de 12 unidades.', 4.60, 'ACTIVE', 'seller-carmen', array['dulces'], array['navidad'], 5),
    ('DUL-003', 'Crème brûlée: kit para 6 raciones', 'Incluye soplete de cocina.', 29.90, 'ACTIVE', 'seller-carmen', array['dulces','hogar'], array['cocina'], 24),
    ('DUL-004', 'Chocolate negro 85 % cacao', 'Origen Ecuador.', 3.75, 'ACTIVE', 'seller-ana', array['dulces'], array['ecologico','comercio-justo'], 31),
    ('DUL-005', 'Mazapán de Toledo', 'Con almendra de Castilla.', 6.30, 'ACTIVE', 'seller-carmen', array['dulces'], array['navidad','denominacion-de-origen'], 5),
    ('DUL-006', 'Miel de azahar cruda 500 g', 'Sin pasteurizar.', 8.90, 'ACTIVE', 'seller-bruno', array['dulces','despensa'], array['ecologico'], 66),
    ('HOG-001', 'Sartén de hierro forjado 28 cm', 'Para toda la vida.', 39.00, 'ACTIVE', 'seller-carmen', array['hogar'], array['cocina'], 80),
    ('HOG-002', 'Paellera esmaltada 40 cm', 'Para 8 personas.', 27.50, 'ACTIVE', 'seller-carmen', array['hogar'], array['cocina'], 75),
    ('HOG-003', 'Juego de cuchillos de cocina', 'Acero inoxidable, 5 piezas.', 54.90, 'ACTIVE', 'seller-carmen', array['hogar'], array['cocina'], 44),
    ('HOG-004', 'Botijo de barro de La Rambla', 'Hecho a mano.', 18.00, 'ACTIVE', 'seller-carmen', array['hogar'], array['artesania'], 52),
    ('HOG-005', 'Mantel de lino bordado', '180 x 250 cm.', 45.00, 'ACTIVE', 'seller-carmen', array['hogar','textil'], array['artesania'], 29),
    ('HOG-006', 'Cesta de mimbre para la compra', 'Asas de cuero.', 22.40, 'ACTIVE', 'seller-carmen', array['hogar'], array['artesania'], 17),
    ('HOG-007', 'Pack_ahorro de bayetas de microfibra', 'Diez unidades.', 6.99, 'ACTIVE', 'seller-carmen', array['hogar'], array['limpieza'], 11),
    ('HOG-008', 'Lámpara de mesa de cerámica', 'Pie pintado a mano.', 64.00, 'DRAFT', 'seller-carmen', array['hogar'], array['artesania'], 0),
    ('TEX-001', 'Manta de lana merina', 'Tejida en telar.', 89.00, 'ACTIVE', 'seller-carmen', array['textil','hogar'], array['artesania'], 58),
    ('TEX-002', 'Alpargatas de esparto', 'Suela cosida a mano.', 19.90, 'ACTIVE', 'seller-carmen', array['textil'], array['artesania'], 13),
    ('TEX-003', 'Delantal de cocina de algodón', 'Con bolsillo frontal.', 15.50, 'ACTIVE', 'seller-carmen', array['textil','hogar'], array['cocina'], 23),
    ('TEX-004', 'Camiseta 100% algodón orgánico', 'Tallas S a XXL.', 17.00, 'ACTIVE', 'seller-carmen', array['textil'], array['ecologico'], 9),
    ('TEX-005', 'Pañuelo de seda estampado', '90 x 90 cm.', 34.00, 'DISCONTINUED', 'seller-carmen', array['textil'], array[]::text[], 250),
    ('ELE-001', 'Auriculares inalámbricos con cancelación de ruido', 'Autonomía de 30 horas.', 129.00, 'ACTIVE', 'seller-diego', array['electronica'], array['audio'], 21),
    ('ELE-002', 'Altavoz Bluetooth resistente al agua', 'IP67.', 49.90, 'ACTIVE', 'seller-diego', array['electronica'], array['audio'], 34),
    ('ELE-003', 'Portátil 14" ligero 16 GB', 'Menos de 1,2 kg.', 899.00, 'ACTIVE', 'seller-diego', array['electronica'], array['informatica'], 16),
    ('ELE-004', 'Ratón ergonómico vertical', 'Recargable por USB-C.', 39.95, 'ACTIVE', 'seller-diego', array['electronica'], array['informatica'], 27),
    ('ELE-005', 'Cafetera espresso automática', 'Molinillo integrado.', 349.00, 'ACTIVE', 'seller-diego', array['electronica','cafe-e-infusiones','hogar'], array['cocina'], 37),
    ('ELE-006', 'Cargador USB-C de 65 W', 'Tecnología GaN.', 34.90, 'ACTIVE', 'seller-diego', array['electronica'], array['informatica'], 4),
    ('ELE-007', 'Libro electrónico con luz cálida', 'Pantalla de 6 pulgadas.', 119.00, 'ACTIVE', 'seller-diego', array['electronica','libros'], array['lectura'], 46),
    ('ELE-008', 'Teclado mecánico en español', 'Distribución ISO con ñ.', 79.00, 'DRAFT', 'seller-diego', array['electronica'], array['informatica'], 0),
    ('NIN-001', 'Puzle de madera del mapa de España', '54 piezas.', 16.50, 'ACTIVE', 'seller-elena', array['ninos'], array['juguetes','educativo'], 39),
    ('NIN-002', 'Cuentos para dormir a los niños pequeños', 'Tapa dura, ilustrado.', 14.90, 'ACTIVE', 'seller-elena', array['ninos','libros'], array['lectura','educativo'], 43),
    ('NIN-003', 'Peonza tradicional de madera', 'Con cuerda de algodón.', 5.50, 'ACTIVE', 'seller-elena', array['ninos'], array['juguetes','artesania'], 61),
    ('NIN-004', 'Mochila escolar infantil', 'Con ruedas.', 32.00, 'ACTIVE', 'seller-carmen', array['ninos','textil'], array[]::text[], 49),
    ('LIB-001', 'Don Quijote de la Mancha, edición anotada', 'Dos volúmenes.', 29.00, 'ACTIVE', 'seller-elena', array['libros'], array['lectura','clasicos'], 100),
    ('LIB-002', 'Cocina española: 500 recetas', 'De la tortilla al cocido.', 24.95, 'ACTIVE', 'seller-elena', array['libros','hogar'], array['cocina','lectura'], 88),
    ('LIB-003', 'Guía del Camino de Santiago', 'Etapas, albergues y mapas.', 19.00, 'ACTIVE', 'seller-elena', array['libros'], array['viajes','lectura'], 53),
    ('LIB-004', 'El arte del café de especialidad', 'Del grano a la taza.', 27.00, 'ACTIVE', 'seller-elena', array['libros','cafe-e-infusiones'], array['lectura'], 3),
    ('LIB-005', 'Guía de İstanbul para viajeros', 'Barrios, mercados y rutas.', 17.90, 'ACTIVE', 'seller-elena', array['libros'], array['viajes','lectura'], 6),
    ('LIB-006', 'Poesía completa de Rosalía de Castro', 'Edición bilingüe.', 22.00, 'DISCONTINUED', 'seller-elena', array['libros'], array['clasicos'], 500)
),
inserted as (
    insert into product (id, sku, slug, name, description, price_amount, price_currency, status,
                         seller_id, created_at, published_at, updated_at, version)
    select gen_random_uuid(),
           s.sku,
           trim(both '-' from regexp_replace(lower(unaccent(s.name)), '[^a-z0-9]+', '-', 'g'))
               || '-' || lower(s.sku),
           s.name,
           s.description,
           s.price,
           'EUR',
           s.status,
           s.seller_id,
           now() - make_interval(days => s.days_ago + 10),
           case when s.status <> 'DRAFT' then now() - make_interval(days => s.days_ago) end,
           now() - make_interval(days => s.days_ago),
           0
    from seed s
    returning id, sku
),
linked as (
    insert into product_category (product_id, category_id)
    select i.id, c.id
    from inserted i
    join seed s on s.sku = i.sku
    cross join lateral unnest(s.categories) as cat(slug)
    join category c on c.slug = cat.slug
    returning product_id
)
insert into product_tag (product_id, tag)
select i.id, t.tag
from inserted i
join seed s on s.sku = i.sku
cross join lateral unnest(s.tags) as t(tag);
