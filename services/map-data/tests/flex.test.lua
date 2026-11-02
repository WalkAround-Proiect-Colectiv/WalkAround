local rows = {}
osm2pgsql = {
    define_table = function(definition)
        assert(definition.schema == 'osm')
        assert(definition.ids.type == 'any' and definition.ids.type_column == 'osm_type')
        return { insert = function(_, row) rows[#rows + 1] = row end }
    end
}
local geometry = {}
geometry.__index = geometry
function geometry:is_null() return self.empty or false end
function geometry:transform() return self end
function geometry:area() return self.size or 200 end
function geometry:centroid() return self end
local function object(tags, size, empty)
    local geom = setmetatable({ size = size, empty = empty }, geometry)
    return { tags = tags, is_closed = true,
        as_point = function() return geom end,
        as_polygon = function() return geom end,
        as_linestring = function() return geom end,
        as_multipolygon = function() return geom end,
        as_multilinestring = function() return geom end }
end
local function expect_last(kind, class, zoom)
    local row = rows[#rows]
    assert(row.kind == kind and row.class == class and row.minzoom == zoom)
end

dofile(arg[1] or '/workspace/importer/flex.lua')
osm2pgsql.process_node(object({ place = 'city', name = 'City', ['name:ro'] = 'Local city' }))
expect_last('place', 'city', 5)
assert(rows[#rows].name == 'Local city')
osm2pgsql.process_way(object({ highway = 'primary', name = 'Road' }))
expect_last('road', 'primary', 6)
osm2pgsql.process_way(object({ building = 'yes' }))
expect_last('building', 'yes', 13)
osm2pgsql.process_relation(object({ type = 'multipolygon', natural = 'water' }, 200000000))
expect_last('water', 'water', 4)
osm2pgsql.process_relation(object({ type = 'multipolygon', landuse = 'forest' }, 2000000))
expect_last('landcover', 'forest', 8)
osm2pgsql.process_node(object({ amenity = 'cafe', name = 'Cafe' }))
expect_last('poi', 'cafe', 15)
osm2pgsql.process_relation(object({ type = 'boundary', boundary = 'administrative', admin_level = '2' }))
expect_last('boundary', '2', 4)
local count = #rows
osm2pgsql.process_way(object({ building = 'no' }))
osm2pgsql.process_way(object({ highway = 'construction' }))
osm2pgsql.process_node(object({ amenity = 'cafe' }))
osm2pgsql.process_way(object({ building = 'yes' }, 0, true))
assert(#rows == count)
print('Flex classification, null-geometry, and zoom tests passed.')