package db.migration;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.*;

public class V3__backfill_dangerous_zone_centers extends BaseJavaMigration
{
    @Override
    public void migrate(Context context) throws Exception
    {
        H3Core h3 = H3Core.newInstance();
        Connection conn = context.getConnection();

        Map<UUID, String> rows = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, h3_index FROM dangerous_zones WHERE center_lat IS NULL"))
        {
            while (rs.next())
            {
                rows.put(rs.getObject("id", UUID.class), rs.getString("h3_index"));
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE dangerous_zones SET center_lat = ?, center_lng = ? WHERE id = ?"))
        {
            for (Map.Entry<UUID, String> e : rows.entrySet())
            {
                LatLng c = h3.cellToLatLng(e.getValue());
                ps.setDouble(1, c.lat);
                ps.setDouble(2, c.lng);
                ps.setObject(3, e.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}