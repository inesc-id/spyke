package spyke.monitor.pcap4j.packet;

import org.pcap4j.packet.namednumber.TcpPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TcpPacket {
    //TODO analyze tcp packets
    private Map<TcpPort, TcpSession> sessions = new HashMap<TcpPort, TcpSession>();
    // TODO code below are not used
    public synchronized void handleTcp(org.pcap4j.packet.TcpPacket tcp){
        try {
            if (tcp == null) {
                System.out.println("Tcp packet null!!!");
                return;
            }

            boolean isToServer = true;
            TcpPort port = tcp.getHeader().getSrcPort();
            if (port.value() == 443) {
                port = tcp.getHeader().getDstPort();
                isToServer = false;
            }

            boolean syn = tcp.getHeader().getSyn();
            boolean fin = tcp.getHeader().getFin();

            if (syn) {
                TcpSession session;
                if (isToServer) {
                    session = new TcpSession();
                    sessions.put(port, session);
                }
                else {
                    session = sessions.get(port);
                }

                long seq = tcp.getHeader().getSequenceNumberAsLong();
                session.setSeqNumOffset(isToServer, seq + 1L);

            }
            else if (fin) {
                TcpSession session = sessions.get(port);
                session.getPackets(isToServer).add(tcp);

                byte[] reassembledPayload
                        = doReassemble(
                        session.getPackets(isToServer),
                        session.getSeqNumOffset(isToServer),
                        tcp.getHeader().getSequenceNumberAsLong(),
                        tcp.getPayload().length()
                );

                int len = reassembledPayload.length;
              /*
              for (int i = 0; i < len;) {
                try {
                  TlsPacket tls = TlsPacket.newPacket(reassembledPayload, i, len - i);
                  System.out.println(tls);
                  i += tls.length();
                } catch (IllegalRawDataException e) {
                  e.printStackTrace();
                }
              }
              */
            }
            else {
                if (tcp.getPayload() != null && tcp.getPayload().length() != 0) {
                    TcpSession session = sessions.get(port);
                    session.getPackets(isToServer).add(tcp);
                }
            }
        } catch (Exception e) {
            System.out.println("Exception -> "+e.getMessage());
        }
    }

    private static byte[] doReassemble(
            List<org.pcap4j.packet.TcpPacket> packets, long seqNumOffset, long lastSeqNum, int lastDataLen
    ) {
        // This cast is not safe.
        // The sequence number is unsigned int and so
        // (int) (lastSeqNum - seqNumOffset) may be negative.
        byte[] buffer = new byte[(int) (lastSeqNum - seqNumOffset) + lastDataLen];

        for (org.pcap4j.packet.TcpPacket p: packets) {
            byte[] payload = p.getPayload().getRawData();
            long seq = p.getHeader().getSequenceNumberAsLong();
            System.arraycopy(payload, 0, buffer, (int) (seq - seqNumOffset), payload.length);
        }

        return buffer;
    }

    public static final class TcpSession {

        private final List<org.pcap4j.packet.TcpPacket> packetsToServer = new ArrayList<org.pcap4j.packet.TcpPacket>();
        private final List<org.pcap4j.packet.TcpPacket> packetsToClient = new ArrayList<org.pcap4j.packet.TcpPacket>();
        private long serverSeqNumOffset;
        private long clientSeqNumOffset;

        public List<org.pcap4j.packet.TcpPacket> getPackets(boolean toServer) {
            if (toServer) {
                return packetsToServer;
            }
            else {
                return packetsToClient;
            }
        }

        public long getSeqNumOffset(boolean toServer) {
            if (toServer) {
                return clientSeqNumOffset;
            }
            else {
                return serverSeqNumOffset;
            }
        }

        public void setSeqNumOffset(boolean toServer, long seqNumOffset) {
            if (toServer) {
                this.clientSeqNumOffset = seqNumOffset;
            }
            else {
                this.serverSeqNumOffset = seqNumOffset;
            }
        }

    }
}
