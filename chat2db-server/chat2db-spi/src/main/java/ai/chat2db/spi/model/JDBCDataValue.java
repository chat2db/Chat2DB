package ai.chat2db.spi.model;

import ai.chat2db.spi.util.ResultSetUtils;
import com.google.common.io.BaseEncoding;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;

/**
 * @author: zgq
 * @date: 2024年05月30日 20:48
 */
@Data
@AllArgsConstructor
public class JDBCDataValue {
    private static final Logger log = LoggerFactory.getLogger(JDBCDataValue.class);
    private ResultSet resultSet;
    private ResultSetMetaData metaData;
    private int columnIndex;
    private boolean limitSize;

    public Object getObject() {
        try {
            return resultSet.getObject(columnIndex);
        } catch (Exception e) {
            log.warn("Failed to retrieve object from database", e);
            try {
                return resultSet.getString(columnIndex);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public String getString() {
        return ResultSetUtils.getString(resultSet, columnIndex);
    }

    public String getType() {
        return ResultSetUtils.getColumnDataTypeName(metaData, columnIndex);
    }

    public InputStream getBinaryStream() {
        return ResultSetUtils.getBinaryStream(resultSet, columnIndex);
    }

    public int getPrecision() {
        return ResultSetUtils.getColumnPrecision(metaData, columnIndex);
    }

    public byte[] getBytes() {
        return ResultSetUtils.getBytes(resultSet, columnIndex);
    }

    public boolean getBoolean() {
        return ResultSetUtils.getBoolean(resultSet, columnIndex);
    }

    public int getScale() {
        return ResultSetUtils.getColumnScale(metaData, columnIndex);
    }

    public int getInt() {
        return ResultSetUtils.getInt(resultSet, columnIndex);
    }

    public Date getDate() {
        return ResultSetUtils.getDate(resultSet, columnIndex);
    }

    public Timestamp getTimestamp() {
        return ResultSetUtils.getTimestamp(resultSet, columnIndex);
    }

    public Clob getClob() {
        return ResultSetUtils.getClob(resultSet, columnIndex);
    }

    public Blob getBlob() {
        return ResultSetUtils.getBlob(resultSet, columnIndex);
    }

    public String getBlobHexString() {
        byte[] bytes = getBytes();
        if (Objects.isNull(bytes)) {
            return null;
        }
        return BaseEncoding.base16().encode(bytes);
    }

    public BigDecimal getBigDecimal() {
        return ResultSetUtils.getBigDecimal(resultSet, columnIndex);
    }

    public String getBigDecimalString() {
        BigDecimal bigDecimal = getBigDecimal();
        return bigDecimal == null ? new String(getBytes()) : bigDecimal.toPlainString();
    }


    public String getBlobString() {
        Blob blob = getBlob();
        try (InputStream binaryStream = blob.getBinaryStream()) {
            long length = blob.length();
            return converterBinaryData(length, binaryStream);
        } catch (SQLException | IOException e) {
            log.warn("Error while reading binary stream", e);
            return getString();
        }
    }


    public String getClobString() {
        Clob clob = getClob();
        try (Reader reader = clob.getCharacterStream()) {
            long length = clob.length();
            LOBInfo cLobInfo = getLobInfo(length);
            double size = cLobInfo.getSize();
            if (size == 0) {
                return "";
            }
            String unit = cLobInfo.getUnit();
            if (limitSize && isBigSize(unit)) {
                return String.format("[%s] %s", getType(), cLobInfo);
            }
            return IOUtils.toString(reader);
        } catch (IOException | SQLException e) {
            log.warn("Error while reading clob stream", e);
            return getStringValue();
        }
    }

    private String handleImageType(InputStream imageStream, LOBInfo lobInfo) {
        if (limitSize) {
            try {
                BufferedImage bufferedImage = ImageIO.read(imageStream);
                return String.format("[%s] %dx%d JPEG image  %s", getType(), bufferedImage.getWidth(), bufferedImage.getHeight(), lobInfo);
            } catch (IOException e) {
                log.warn("Error while reading image stream", e);
                return getStringValue();
            }
        } else {
            return "0x" + getBlobHexString();
        }
    }

    private String handleStringType(InputStream binaryStream, LOBInfo lobInfo) throws IOException {
        if (isBigSize(lobInfo.getUnit()) && limitSize) {
            return String.format("[%s] %s", getType(), lobInfo);
        } else {
            return new String(binaryStream.readAllBytes());
        }
    }

    private boolean isBigSize(String unit) {
        return LobUnit.G.unit.equals(unit) || LobUnit.M.unit.equals(unit);
    }


    @NotNull
    private LOBInfo getLobInfo(long size) {
        if (size == 0) {
            return new LOBInfo(LobUnit.B.unit, 0);
        }
        return new LOBInfo(size);
    }

    public String getStringValue() {
        return ResultSetUtils.getStringValue(resultSet, columnIndex);
    }

    public String getBinaryDataString() {
        InputStream binaryStream = null;
        try {
            binaryStream = getBinaryStream();
            if (Objects.isNull(binaryStream)) {
                return null;
            }
            // 检查流是否支持 mark 操作，不支持则用 BufferedInputStream 包装
            if (!binaryStream.markSupported()) {
                binaryStream = new BufferedInputStream(binaryStream);
            }

            binaryStream.mark(Integer.MAX_VALUE);

            long size = 0;
            byte[] buffer = new byte[8192]; // 缓冲区
            int bytesRead;
            while ((bytesRead = binaryStream.read(buffer)) != -1) {
                size += bytesRead;
            }
            binaryStream.reset(); // 重置流到标记的位置
            return converterBinaryData(size, binaryStream);
        } catch (SQLException | IOException e) {
            log.warn("Error while reading binary stream", e);
            return getStringValue();
        } finally {
            // 关闭流
            if (binaryStream != null) {
                try {
                    binaryStream.close();
                } catch (IOException e) {
                    log.warn("Error while closing binary stream", e);
                }
            }
        }
    }

    private String converterBinaryData(long size, InputStream binaryStream) throws IOException, SQLException {
        LOBInfo lobInfo = getLobInfo(size);
        String unit = lobInfo.unit;
        if (size == 0) {
            return "";
        }
        Tika tika = new Tika();
        String contentType = tika.detect(binaryStream);
        FileTypeEnum fileTypeEnum = FileTypeEnum.fromDescription(contentType);
        if (Objects.isNull(fileTypeEnum)) {
            if (isBigSize(unit) && limitSize) {
                return String.format("[%s] %s", getType(), lobInfo);
            }
            return "0x" + BaseEncoding.base16().encode(binaryStream.readAllBytes());
        }

        return switch (fileTypeEnum) {
            case IMAGE -> handleImageType(binaryStream, lobInfo);
            case STRING -> handleStringType(binaryStream, lobInfo);
            default -> "";
        };
    }


    @Getter
    public enum LobUnit {
        B("B", 1L),
        K("KB", 1024L),
        M("MB", 1024L * 1024L),
        G("GB", 1024L * 1024L * 1024L);

        private final String unit;
        private final long size;

        LobUnit(String unit, long size) {
            this.unit = unit;
            this.size = size;
        }

    }

    @Getter
    public static class LOBInfo {
        private final String unit;
        private final double size;

        public LOBInfo(String unit, double size) {
            this.unit = unit;
            this.size = size;
        }

        public LOBInfo(long size) {
            if (size >= LobUnit.G.size) {
                this.unit = LobUnit.G.unit;
                this.size = (double) size / LobUnit.G.size;
            } else if (size >= LobUnit.M.size) {
                this.unit = LobUnit.M.unit;
                this.size = (double) size / LobUnit.M.size;
            } else if (size >= LobUnit.K.size) {
                this.unit = LobUnit.K.unit;
                this.size = (double) size / LobUnit.K.size;
            } else {
                this.unit = LobUnit.B.unit;
                this.size = (double) size;
            }
        }

        @Override
        public String toString() {
            return String.format("%.2f %s", size, unit);
        }
    }

    @Getter
    public enum FileTypeEnum {
        IMAGE("image/jpeg"),
        STRING("text/plain"),
        ;
        private final String description;

        FileTypeEnum(String description) {
            this.description = description;
        }

        public static FileTypeEnum fromDescription(String description) {
            for (FileTypeEnum fileType : values()) {
                if (fileType.description.equals(description)) {
                    return fileType;
                }
            }
            return null;
        }
    }
}
