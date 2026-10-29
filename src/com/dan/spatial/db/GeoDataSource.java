package com.dan.spatial.db;

import com.dan.spatial.geo.GeoFeature;
import com.dan.spatial.geo.GeometryConverter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Lädt Geo-Daten aus Oracle 21c (Thin-Driver).
 *
 * Voraussetzungen im Classpath:
 *   ojdbc11.jar   – JDBC-Driver
 *   sdoapi.jar    – JGeometry
 *   sdoutl.jar    – SDO-Utilities (optional)
 *
 * Schema: DB02, Passwort: db
 * Service: PDBORCL (Standard-Port 1521)
 */
public class GeoDataSource {

    // ---------- Verbindungsparameter (anpassen falls nötig) ----------
    private static final String HOST     = "localhost";
    private static final int    PORT     = 1521;
    private static final String SERVICE  = "PDBORCL";
    private static final String USER     = "DB02";
    private static final String PASSWORD = "db";

    private static final String URL =
        "jdbc:oracle:thin:@" + HOST + ":" + PORT + "/" + SERVICE;

    // ---------- SQL ----------
    private static final String SQL_ADM0 =
        "SELECT SHAPENAME, SHAPEISO, SHAPEID, SHAPEGROUP, SHAPETYPE, GEOM " +
        "FROM GEOBOUNDARIES_DEU_ADM0";

    private static final String SQL_ADM1 =
        "SELECT SHAPENAME, SHAPEISO, SHAPEID, SHAPEGROUP, SHAPETYPE, GEOM " +
        "FROM GEOBOUNDARIES_DEU_ADM1 ORDER BY SHAPENAME";

    // ----------------------------------------------------------
    // Public API
    // ----------------------------------------------------------

    /** Lädt die Deutschland-Außengrenze (ADM0, i.d.R. 1 Feature). */
    public List<GeoFeature> loadADM0() throws Exception {
        return load(SQL_ADM0, "ADM0");
    }

    /** Lädt alle 16 Bundesland-Grenzen (ADM1). */
    public List<GeoFeature> loadADM1() throws Exception {
        return load(SQL_ADM1, "ADM1");
    }

    // ----------------------------------------------------------
    // Intern
    // ----------------------------------------------------------

    private List<GeoFeature> load(String sql, String defaultType) throws Exception {
        List<GeoFeature> result = new ArrayList<>();

        // Oracle JDBC-Driver laden (Reflection, damit Compile ohne ojdbc klappt)
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("ojdbc11.jar fehlt im Classpath: " + e.getMessage());
        }

        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                GeoFeature f = new GeoFeature();
                f.shapeName  = nullSafe(rs.getString("SHAPENAME"));
                f.shapeISO   = nullSafe(rs.getString("SHAPEISO"));
                f.shapeID    = nullSafe(rs.getString("SHAPEID"));
                f.shapeGroup = nullSafe(rs.getString("SHAPEGROUP"));
                f.shapeType  = nullSafe(rs.getString("SHAPETYPE"), defaultType);

                // SDO_GEOMETRY → JGeometry (Reflection)
                Object struct = rs.getObject("GEOM");
                if (struct != null) {
                    f.rawGeom   = toJGeometry(struct, conn);
                    f.geoBounds = GeometryConverter.extractBounds(f.rawGeom);
                    // Paths werden lazy in FSpatialMap berechnet (Projektion noch unbekannt)
                }
                result.add(f);
            }
            rs.close();
            stmt.close();
        } finally {
            conn.close();
        }
        return result;
    }

    /**
     * Konvertiert einen oracle.sql.STRUCT in JGeometry via Reflection.
     * Spart eine direkte Compile-Abhängigkeit auf sdoapi.jar.
     */
    private static Object toJGeometry(Object struct, Connection conn) {
        try {
            // oracle.spatial.geometry.JGeometry.load(STRUCT)
            Class<?> jgClass = Class.forName("oracle.spatial.geometry.JGeometry");
            java.lang.reflect.Method loadMethod =
                jgClass.getMethod("load", Class.forName("oracle.sql.STRUCT"));
            return loadMethod.invoke(null, struct);
        } catch (Exception e) {
            System.err.println("JGeometry.load() Fehler: " + e.getMessage());
            return null;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullSafe(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s.trim();
    }
}
