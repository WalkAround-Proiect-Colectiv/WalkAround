local features = osm2pgsql.define_table({
    name = 'features',
    schema = 'osm',
    ids = { type = 'any', id_column = 'osm_id', type_column = 'osm_type' },
    columns = {
        { column = 'kind', type = 'text', not_null = true },
        { column = 'class', type = 'text', not_null = true },
        { column = 'name', type = 'text' },
        { column = 'minzoom', type = 'int', not_null = true },
        { column = 'geom', type = 'geometry', projection = 3857, not_null = true }
    }
})

local road_zoom = { motorway = 4, motorway_link = 9, trunk = 5, trunk_link = 10,
    primary = 6, primary_link = 11, secondary = 8, secondary_link = 12,
    tertiary = 10, tertiary_link = 12, residential = 12, unclassified = 12 }
local place_zoom = { country = 4, state = 6, city = 5, town = 8, village = 11,
    hamlet = 13, suburb = 12, neighbourhood = 14 }

local function add(object, kind, class, minzoom, geom)
    if geom:is_null() then return end
    features:insert({ kind = kind, class = class, name = object.tags['name:ro'] or object.tags.name,
        minzoom = minzoom, geom = geom })
end

local function poi_class(tags)
    return tags.amenity or tags.tourism or tags.shop
end

local function area(object, geom)
    if geom:is_null() or object.tags.area == 'no' then return end
    local tags = object.tags
    local size = geom:transform(3857):area()
    local minzoom = 12
    if size >= 100000000 then minzoom = 4
    elseif size >= 10000000 then minzoom = 6
    elseif size >= 1000000 then minzoom = 8
    elseif size >= 100000 then minzoom = 10 end
    if tags.building and tags.building ~= 'no' then
        add(object, 'building', tags.building, 13, geom)
    elseif tags.natural == 'water' or tags.water or tags.waterway == 'riverbank' or tags.landuse == 'reservoir' then
        add(object, 'water', tags.water or 'water', minzoom, geom)
    elseif tags.landuse or tags.landcover or tags.natural == 'wood' or tags.natural == 'grassland'
        or tags.leisure == 'park' or tags.leisure == 'garden' then
        add(object, 'landcover', tags.landuse or tags.landcover or tags.natural or tags.leisure, minzoom, geom)
    end
    local poi = poi_class(tags)
    if poi and tags.name then add(object, 'poi', poi, 15, geom:centroid()) end
end

function osm2pgsql.process_node(object)
    local tags = object.tags
    if tags.place and place_zoom[tags.place] then
        add(object, 'place', tags.place, place_zoom[tags.place], object:as_point())
    end
    local poi = poi_class(tags)
    if poi and tags.name then add(object, 'poi', poi, 15, object:as_point()) end
end

function osm2pgsql.process_way(object)
    local tags = object.tags
    if tags.highway and tags.area ~= 'yes' and tags.highway ~= 'proposed' and tags.highway ~= 'construction' then
        add(object, 'road', tags.highway, road_zoom[tags.highway] or 14, object:as_linestring())
    end
    if tags.waterway then
        add(object, 'waterway', tags.waterway, tags.waterway == 'river' and 8 or 13, object:as_linestring())
    end
    if tags.natural == 'coastline' then add(object, 'coastline', 'coastline', 4, object:as_linestring()) end
    if object.is_closed then area(object, object:as_polygon()) end
end

function osm2pgsql.process_relation(object)
    if object.tags.type == 'multipolygon' then area(object, object:as_multipolygon()) end
    if object.tags.type == 'boundary' and object.tags.boundary == 'administrative' then
        local level = tonumber(object.tags.admin_level)
        if level and level <= 6 then
            add(object, 'boundary', tostring(level), level <= 2 and 4 or (level <= 4 and 7 or 10), object:as_multilinestring())
        end
    end
end