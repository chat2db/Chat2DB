package ai.chat2db.plugin.mariadb.value.sub;

import ai.chat2db.plugin.mysql.value.template.MysqlDmlValueTemplate;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * @author: zgq
 * @date: 2024年06月01日 12:42
 */
public class MariaDBGeometryProcessor extends DefaultValueProcessor {


    private static final Logger log = LoggerFactory.getLogger(MariaDBGeometryProcessor.class);

    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return MysqlDmlValueTemplate.wrapGeometry(dataValue.getValue());
    }

    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        try {
            Geometry dbGeometry = null;
            byte[] geometryAsBytes = dataValue.getBytes();
            if (geometryAsBytes != null) {
                if (geometryAsBytes.length < 5) {
                    throw new Exception("Invalid geometry inputStream - less than five bytes");
                }

                //first four bytes of the geometry are the SRID,
                //followed by the actual WKB.  Determine the SRID
                //here
                byte[] sridBytes = new byte[4];
                System.arraycopy(geometryAsBytes, 0, sridBytes, 0, 4);
                boolean bigEndian = (geometryAsBytes[4] == 0x00);

                int srid = 0;
                if (bigEndian) {
                    for (int i = 0; i < sridBytes.length; i++) {
                        srid = (srid << 8) + (sridBytes[i] & 0xff);
                    }
                } else {
                    for (int i = 0; i < sridBytes.length; i++) {
                        srid += (sridBytes[i] & 0xff) << (8 * i);
                    }
                }

                //use the JTS WKBReader for WKB parsing
                WKBReader wkbReader = new WKBReader();

                //copy the byte array, removing the first four
                //SRID bytes
                byte[] wkb = new byte[geometryAsBytes.length - 4];
                System.arraycopy(geometryAsBytes, 4, wkb, 0, wkb.length);
                dbGeometry = wkbReader.read(wkb);
                dbGeometry.setSRID(srid);
            }
            return dbGeometry != null ? dbGeometry.toString() : null;
        } catch (Exception e) {
            log.warn("Error converting database geometry", e);
            return dataValue.getStringValue();
        }
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return MysqlDmlValueTemplate.wrapGeometry(convertJDBCValueByType(dataValue));
    }

}
