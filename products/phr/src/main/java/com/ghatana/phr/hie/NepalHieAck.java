package com.ghatana.phr.hie;

/**
 * @doc.type record
 * @doc.purpose Parsed acknowledgment returned by Nepal HIE after HL7 submission
 * @doc.layer product
 * @doc.pattern DTO
 */
public record NepalHieAck(String messageControlId, String acknowledgementCode, String textMessage) {

    public boolean accepted() {
        return "AA".equalsIgnoreCase(acknowledgementCode) || "CA".equalsIgnoreCase(acknowledgementCode);
    }

    public static NepalHieAck parse(String ackPayload) {
        String[] segments = ackPayload.split("\\r|\\n");
        for (String segment : segments) {
            if (segment.startsWith("MSA|")) {
                String[] fields = segment.split("\\|", -1);
                String ackCode = fields.length > 1 ? fields[1] : "AE";
                String controlId = fields.length > 2 ? fields[2] : "unknown";
                String text = fields.length > 3 ? fields[3] : "Acknowledgment received";
                return new NepalHieAck(controlId, ackCode, text);
            }
        }
        throw new IllegalArgumentException("Nepal HIE acknowledgment did not contain an MSA segment");
    }
}