//package ai.chat2db.plugin.mysql.value;
//
//import ai.chat2db.spi.ValueHandler;
//import org.locationtech.jts.geom.Geometry;
//import org.locationtech.jts.io.WKBReader;
//
//import java.io.InputStream;
//import java.io.ByteArrayOutputStream;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//
//public class GeometryValueHandler implements ValueHandler {
//    @Override
//    public String getString(ResultSet rs, int index, boolean limitSize) throws SQLException {
//        try {
//            InputStream inputStream = rs.getBinaryStream(index);
//            Geometry dbGeometry = null;
//            if (inputStream != null) {
//
//                //convert the stream to a byte[] array
//                //so it can be passed to the WKBReader
//                byte[] buffer = new byte[255];
//
//                int bytesRead = 0;
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                while ((bytesRead = inputStream.read(buffer)) != -1) {
//                    baos.write(buffer, 0, bytesRead);
//                }
//
//                byte[] geometryAsBytes = baos.toByteArray();
//
//                if (geometryAsBytes.length < 5) {
//                    throw new Exception("Invalid geometry inputStream - less than five bytes");
//                }
//
//                //first four bytes of the geometry are the SRID,
//                //followed by the actual WKB.  Determine the SRID
//                //here
//                byte[] sridBytes = new byte[4];
//                System.arraycopy(geometryAsBytes, 0, sridBytes, 0, 4);
//                boolean bigEndian = (geometryAsBytes[4] == 0x00);
//
//                int srid = 0;
//                if (bigEndian) {
//                    for (int i = 0; i < sridBytes.length; i++) {
//                        srid = (srid << 8) + (sridBytes[i] & 0xff);
//                    }
//                } else {
//                    for (int i = 0; i < sridBytes.length; i++) {
//                        srid += (sridBytes[i] & 0xff) << (8 * i);
//                    }
//                }
//
//                //use the JTS WKBReader for WKB parsing
//                WKBReader wkbReader = new WKBReader();
//
//                //copy the byte array, removing the first four
//                //SRID bytes
//                byte[] wkb = new byte[geometryAsBytes.length - 4];
//                System.arraycopy(geometryAsBytes, 4, wkb, 0, wkb.length);
//                dbGeometry = wkbReader.read(wkb);
//                dbGeometry.setSRID(srid);
//            }
//            return dbGeometry.toString();
//        } catch (Exception e) {
//            return rs.getString(index);
//        }
//    }
//}
