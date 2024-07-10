//package ai.chat2db.spi.sql;
//
//import org.bson.json.Converter;
//import org.bson.json.StrictJsonWriter;
//import org.bson.types.ObjectId;
//
//public class MongExtendedJsonObjectIdConverter implements Converter<ObjectId> {
//    @Override
//    public void convert(final ObjectId value, final StrictJsonWriter writer) {
//        writer.writeStartObject();
//        writer.writeString("", value.toHexString());
//        writer.writeEndObject();
//    }
//}
