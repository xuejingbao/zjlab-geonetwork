/* Copyright 2018 Rendits
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.rendits.router;

import net.gcdc.asn1.uper.UperEncoder;
import net.gcdc.camdenm.CoopIts.Cam;
import net.gcdc.camdenm.CoopIts.Denm;
import net.gcdc.camdenm.CoopIts.ItsPduHeader.MessageId;
import net.gcdc.camdenm.Iclcm;
import net.gcdc.camdenm.Iclcm.IgameCooperativeLaneChangeMessage;
import net.gcdc.geonetworking.*;
import net.gcdc.geonetworking.Destination.Geobroadcast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 *
 * <h1>Rendits vehicle router</h1>
 *
 * <p>This class is built to work as an ITS-G5 V2X gateway. Specifically this class will do two
 * things: Receive incoming V2X messages from other vehicles, decode the received message and
 * forward it to the local control system. It will also listen for messages from the local control
 * system and forward those encoded according to the ITS-G5 specification.
 *
 * @author Albin Severinson (albin@rendits.com)
 * @version 1.0.0-SNAPSHOT
 */
public class Router {
  private static final Logger logger = LoggerFactory.getLogger(Router.class);
  private final Thread stationThread;
  private final GeonetStation station;

  /* Incoming UDP messages */
  private final DatagramSocket rcvSocket;
  private static final int MAX_UDP_LENGTH = 600;

  /* Incoming/outgoing BTP messages */
  private final BtpSocket btpSocket;

  /* BTP ports for CAM/DENM/iCLCM/CUSTOM */
  private static final short PORT_CAM = 2001;
  private static final short PORT_DENM = 2002;
  private static final short PORT_CUSTOM = 2003;
  private static final short PORT_ICLCM = 2010;

  /* Custom message ID */
  private static final byte CUSTOM_MESSAGE_ID = 10;

  /* Message lifetime */
  private static final double CAM_LIFETIME_SECONDS = 0.9;
  private static final double iCLCM_LIFETIME_SECONDS = 0.9;
  private static final double CUSTOM_LIFETIME_SECONDS = 0.9;

  /* Default ports */
  private int vehicleCamPort = 5000;
  private int vehicleDenmPort = 5000;
  private int vehicleIclcmPort = 5000;
  private int vehicleCustomPort = 5000;
  private InetAddress vehicleAddress;

  /* Thread pool for all workers handling incoming/outgoing messages */
  private ExecutorService executor;

  /* For keeping track of the current vehicle position. Used for the
   * broadcasting service and for generating Geonetworking addresses.
   */
  private VehiclePositionProvider vehiclePositionProvider;

  /* True while the threads should be running */
  private volatile boolean running;

  /**
   * Router constructor. This method will initialize the GeoNetworking stack and set up everything
   * such that it is ready to accept messages.
   *
   * @param props A Java Properties object. See the example config file router.properties for the
   *     required properties.
   * @exception IOException on error setting up the sockets or on missing required parameters.
   */
  public Router(Properties props) throws IOException {

    /* Set running status to true */
    running = true;

    /* Start the thread pool */
    executor = Executors.newCachedThreadPool();

    /* Configure vehicle address */
    String vehicleAddressString = props.getProperty("vehicleAddress");
    vehicleAddress = InetAddress.getByName(vehicleAddressString);

    /* Router mac address */
    MacAddress senderMac = new MacAddress(props.getProperty("macAddress"));

    /* Configure router address */
    int countryCode = Integer.parseInt(props.getProperty("countryCode"));
    Address address =
        new Address(
            true, // isManual,
            StationType.values()[5], // 5 for passenger car
            countryCode,
            senderMac.value());

    /* Create a vehicle position provider */
    vehiclePositionProvider = new VehiclePositionProvider(address);

    /* Set the specified ports */
    vehicleCamPort = Integer.parseInt(props.getProperty("portSendCam"));
    vehicleDenmPort = Integer.parseInt(props.getProperty("portSendDenm"));
    vehicleIclcmPort = Integer.parseInt(props.getProperty("portSendIclcm"));
    vehicleCustomPort = Integer.parseInt(props.getProperty("portSendCustom"));

    /* Open the receive socket */
    int portRcvFromVehicle = Integer.parseInt(props.getProperty("portRcvFromVehicle"));

    /* Create a new config */
    StationConfig config = new StationConfig();

    /* Configure the link layer */
    int localPortForUdpLinkLayer = Integer.parseInt(props.getProperty("localPortForUdpLinkLayer"));
    InetSocketAddress remoteAddressForUdpLinkLayer =
        new SocketAddressFromString(props.getProperty("remoteAddressForUdpLinkLayer"))
            .asInetSocketAddress();
    LinkLayer linkLayer =
        new LinkLayerUdpToEthernet(localPortForUdpLinkLayer, remoteAddressForUdpLinkLayer, true);

    /* Start the GeoNet station */
    rcvSocket = new DatagramSocket(portRcvFromVehicle);
    station = new GeonetStation(config, linkLayer, vehiclePositionProvider, senderMac);
    stationThread = new Thread(station);
    stationThread.start();

    /* Turn on the beaconing service. It transmits beacons while
     * nothing else is transmitting.
     */
    station.startBecon();

    /* Start the BTP socket */
    btpSocket = BtpSocket.on(station);

    /* Start the loops that handle sending and receiving messages */
    int numReceiveThreads = Integer.parseInt(props.getProperty("receiveThreads", "1"));
    if (numReceiveThreads != 1) {
        this.close();
        throw new IllegalArgumentException("there must be exactly 1 receive thread."
                                           + " check the properties file.");
    }
    for (int i = 0; i < numReceiveThreads; i++) {
      executor.submit(receiveFromVehicle);
    }

    int numSendThreads = Integer.parseInt(props.getProperty("sendThreads", "1"));
    if (numSendThreads <= 0) {
        this.close();
        throw new IllegalArgumentException("there must be at least 1 send thread."
                                           + " check the properties file.");
    }
    for (int i = 0; i < numSendThreads; i++) {
      executor.submit(sendToVehicle);
    }

    /* Start thread that handles printing statistics to the log */
    statsLogger = new StatsLogger(executor);
  }

  /**
   * Stop the GeoNetworking stack and the router. The program will shut down after calling this if
   * nothing else is running.
   */
  public void close() {

    /* Notify all threads to stop running */
    running = false;

    /* Shutdown the GeoNet station */
    station.close();
    stationThread.interrupt();

    /* Close the sockets */
    rcvSocket.close();
    btpSocket.close();

    /* Shutdown the thread pool */
    executor.shutdown();

    /* Give the threads 1 second before shutting down forcefully. */
    try {
      executor.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.error("Router interrupted during shutdown:", e);
    }
    executor.shutdownNow();

    logger.info("Router closed");
  }

  /**
   * This class is used to keep track of statistics. It maintains counts of the how many messages
   * have passed of each kind and provides methods to increment the counts. The statistics are
   * written to log periodically.
   */
  private StatsLogger statsLogger;

  private class StatsLogger {
    private AtomicInteger txCam = new AtomicInteger();
    private AtomicInteger rxCam = new AtomicInteger();
    private AtomicInteger txDenm = new AtomicInteger();
    private AtomicInteger rxDenm = new AtomicInteger();
    private AtomicInteger txIclcm = new AtomicInteger();
    private AtomicInteger rxIclcm = new AtomicInteger();
    private AtomicInteger txCustom = new AtomicInteger();
    private AtomicInteger rxCustom = new AtomicInteger();

    /**
     * StatsLogger constructor.
     *
     * @param executor Executor service the thread writing stats to log will be added to.
     */
    StatsLogger(ExecutorService executor) {
      executor.submit(logStats);
    }

    /** Increment the count of transmitted CAM messages. */
    public void incTxCam() {
      this.txCam.incrementAndGet();
    }

    /** Increment the count of received CAM messages. */
    public void incRxCam() {
      this.rxCam.incrementAndGet();
    }

    /** Increment the count of transmitted DENM messages. */
    public void incTxDenm() {
      this.txDenm.incrementAndGet();
    }

    /** Increment the count of received DENM messages. */
    public void incRxDenm() {
      this.rxDenm.incrementAndGet();
    }

    /** Increment the count of transmitted ICLCM messages. */
    public void incTxIclcm() {
      this.txIclcm.incrementAndGet();
    }

    /** Increment the count of received ICLCM messages. */
    public void incRxIclcm() {
      this.rxIclcm.incrementAndGet();
    }

    /** Increment the count of transmitted custom messages. */
    public void incTxCustom() {
      this.txCustom.incrementAndGet();
    }

    /** Increment the count of received custom messages. */
    public void incRxCustom() {
      this.rxCustom.incrementAndGet();
    }

    /** Dedicated thread for periodically logging statistics. */
    private Runnable logStats =
        new Runnable() {
          @Override
          public void run() {

            /* Chill out for a bit to let everything else start
             * before logging anything.
             */
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              logger.warn("Statistics logger interrupted during sleep.");
            }

            /* Print startup message */
            System.out.println(
                "#### Rendits Vehicle Router ####"
                    + "\nListening on port "
                    + rcvSocket.getLocalPort()
                    + "\nVehicle Control System IP is "
                    + vehicleAddress
                    + "\nSending incoming CAM to port "
                    + vehicleCamPort
                    + "\nSending incoming DENM to port "
                    + vehicleDenmPort
                    + "\nSending incoming iCLCM to port "
                    + vehicleIclcmPort
                    + "\nSending incoming custom messages to port "
                    + vehicleCustomPort
                    + "\nCopyright: Rendits (albin@rendits.com)");

            /* Log statistics every second */
            while (running) {
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                logger.warn("Statistics logger interrupted during sleep.");
              }

              /* Log stats */
              logger.info(
                  "#CAM (Tx/Rx): {}/{} "
                      + "| #DENM (Tx/Rx): {}/{} "
                      + "| #iCLCM (Tx/Rx): {}/{} "
                      + "| #Custom (Tx/Rx): {}/{}",
                  txCam,
                  rxCam,
                  txDenm,
                  rxDenm,
                  txIclcm,
                  rxIclcm,
                  txCustom,
                  rxCustom);
            }
          }
        };
  }

  /**
   * Parse the byte[] representation of a simple message into a proper ITS-G5 message and transmit
   * it.
   *
   * @param buffer byte[] representation of a simple message.
   */
  private void properFromSimple(byte[] buffer) {
    switch (buffer[0]) {
      case MessageId.cam:
        try {
          SimpleCam simpleCam = new SimpleCam(buffer);
          Cam cam = simpleCam.asCam();
          send(cam);
          statsLogger.incTxCam();

          /* Use the data in the CAM to update the locally
           * stored vehicle position. Used when receiving
           * messages and generating adresses.
           */
          double latitude = (double) simpleCam.getLatitude();
          latitude /= 1e7;

          double longitude = (double) simpleCam.getLongitude();
          longitude /= 1e7;

          double speedMetersPerSecond = (double) simpleCam.speed;
          speedMetersPerSecond *= 100;

          double headingDegreesFromNorth = (double) simpleCam.heading;
          headingDegreesFromNorth *= 10;

          vehiclePositionProvider.update(
              latitude, longitude, speedMetersPerSecond, headingDegreesFromNorth);
        } catch (IllegalArgumentException e) {
          logger.error("Irrecoverable error when creating CAM. Ignoring message.", e);
        }
        break;

      case MessageId.denm:
        try {
          SimpleDenm simpleDenm = new SimpleDenm(buffer);
          Denm denm = simpleDenm.asDenm();

          /* Simple messages are sent to everyone within range. */
          Position position = vehiclePositionProvider.getPosition();
          int max_radius = simpleDenm.semiMajorConfidence;
          Area target = Area.circle(position, max_radius);
          send(denm, Geobroadcast.geobroadcast(target));
          statsLogger.incTxDenm();

        } catch (IllegalArgumentException e) {
          logger.error("Irrecoverable error when creating DENM. Ignoring message.", e);
        }
        break;

      case Iclcm.MessageID_iCLCM:
        try {
          SimpleIclcm simpleIclcm = new SimpleIclcm(buffer);
          IgameCooperativeLaneChangeMessage iclcm = simpleIclcm.asIclcm();
          send(iclcm);
          statsLogger.incTxIclcm();

        } catch (IllegalArgumentException e) {
          logger.error("Irrecoverable error when creating iCLCM. Ignoring message.", e);
        }
        break;

      /*case CUSTOM_MESSAGE_ID:
        statsLogger.incTxCustom();
        send(buffer);
        break;*/

      default:
        logger.warn("Received incorrectly formatted message. First byte: {}", buffer[0]);
    }
  }

  /**
   * Receive simple messages from the control system, parse them into the proper message
   * (CAM/DENM/iCLCM/custom) and forward to the link layer.
   */
  private Runnable receiveFromVehicle =
      new Runnable() {
        private final byte[] buffer = new byte[MAX_UDP_LENGTH];
        private final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        @Override
        public void run() {
          logger.info("Receive thread starting...");
          while (running) {
            try {
              rcvSocket.receive(packet);
              byte[] receivedData =
                  Arrays.copyOfRange(
                      packet.getData(),
                      packet.getOffset(),
                      packet.getOffset() + packet.getLength());

              if (receivedData.length != packet.getLength()) {
                logger.warn(
                    "The length of the received data did not match the packet length. Dropping packet.");
                continue;
              }

              /* Parse data and send forward message */
              properFromSimple(receivedData);

            } catch (IOException e) {
              logger.error("Exception when receiving message from vehicle");

              /* Sleep for a short time whenever an
               * IO exception occurs.
               */
              try {
                Thread.sleep(100);
              } catch (InterruptedException ee) {
                logger.warn("Interrupted during sleep");
              }
            }
          }
          logger.info("Receive thread closing!");
        }
      };

  /**
   * Parse a proper ITS-G5 message into its simple message representation. The simple message is
   * forwarded to the local control system.
   *
   * @param payload The payload of a received BTP message. The payload should be an ASN.1 encoded
   *     CAM/DENM/iCLCM message or a custom message.
   * @param destinationPort Port to send the simple message to.
   * @param packet The packet to use when sending the simple message.
   * @param toVehicleSocket The socket to use when sending the simple message,
   */
  private void simpleFromProper(
      byte[] payload, int destinationPort, DatagramPacket packet, DatagramSocket toVehicleSocket) {
    switch (destinationPort) {
      case PORT_CAM:
        try {
          Cam cam = UperEncoder.decode(payload, Cam.class);
          SimpleCam simpleCam = new SimpleCam(cam);
          byte[] buffer = simpleCam.asByteArray();
          packet.setData(buffer, 0, buffer.length);
          packet.setPort(vehicleCamPort);

          try {
            toVehicleSocket.send(packet);
            statsLogger.incRxCam();
          } catch (IOException e) {
            logger.warn("Failed to send CAM to vehicle", e);
          }
        } catch (NullPointerException
            | IllegalArgumentException
            | UnsupportedOperationException
            | BufferOverflowException e) {
          logger.warn("Couldn't decode CAM:", e);
        }
        break;

      case PORT_DENM:
        try {
          Denm denm = UperEncoder.decode(payload, Denm.class);
          SimpleDenm simpleDenm = new SimpleDenm(denm);
          byte[] buffer = simpleDenm.asByteArray();
          packet.setData(buffer, 0, buffer.length);
          packet.setPort(vehicleDenmPort);

          try {
            toVehicleSocket.send(packet);
            statsLogger.incRxDenm();
          } catch (IOException e) {
            logger.warn("Failed to send DENM to vehicle", e);
          }
        } catch (NullPointerException
            | IllegalArgumentException
            | UnsupportedOperationException
            | BufferOverflowException e) {
          logger.warn("Couldn't decode DENM:", e);
        }
        break;

      case PORT_ICLCM:
        try {
          IgameCooperativeLaneChangeMessage iclcm =
              UperEncoder.decode(payload, IgameCooperativeLaneChangeMessage.class);
          SimpleIclcm simpleIclcm = new SimpleIclcm(iclcm);
          byte[] buffer = simpleIclcm.asByteArray();
          packet.setData(buffer, 0, buffer.length);
          packet.setPort(vehicleIclcmPort);

          try {
            toVehicleSocket.send(packet);
            statsLogger.incRxIclcm();
          } catch (IOException e) {
            logger.warn("Failed to send iCLCM to vehicle", e);
          }
        } catch (NullPointerException
            | IllegalArgumentException
            | UnsupportedOperationException
            | BufferOverflowException e) {
          logger.warn("Couldn't decode iCLCM:", e);
        }
        break;

      case PORT_CUSTOM:
        packet.setData(payload, 0, payload.length);
        packet.setPort(vehicleCustomPort);

        try {
          toVehicleSocket.send(packet);
          statsLogger.incRxCustom();
        } catch (IOException e) {
          logger.warn("Failed to send custom message to vehicle", e);
        }
        break;

      default:
        // fallthrough
    }
  }

  /**
   * Receive incoming proper CAM/DENM/iCLCM, parse them into simple messages and forward them to the
   * local control system.
   */
  private Runnable sendToVehicle =
      new Runnable() {
        private final byte[] buffer = new byte[MAX_UDP_LENGTH];
        private final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        @Override
        public void run() {
          logger.info("Send thread starting...");
          packet.setAddress(vehicleAddress);
          try {
            while (running) {
              BtpPacket btpPacket = btpSocket.receive();
              byte[] payload = btpPacket.payload();
              int destinationPort = btpPacket.destinationPort();
              simpleFromProper(payload, destinationPort, packet, rcvSocket);
            }
          } catch (InterruptedException e) {
            logger.warn("BTP socket interrupted during receive");
          }
          logger.info("Send thread closing!");
        }
      };

  /**
   * Broadcast a proper CAM message.
   *
   * @param cam A proper CAM message.
   */
  public void send(Cam cam) {
    byte[] bytes;
    try {
      bytes = UperEncoder.encode(cam);
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      logger.warn("Failed to encode CAM {}, ignoring", cam, e);
      return;
    }
    BtpPacket packet = BtpPacket.singleHop(bytes, PORT_CAM, CAM_LIFETIME_SECONDS);
    try {
      btpSocket.send(packet);
    } catch (IOException e) {
      logger.warn("failed to send cam", e);
    }
  }

  /**
   * Broadcast a proper DENM message to the specified GeoBroadcast destination.
   *
   * @param denm A proper DENM message.
   * @param destination The geographical destination of the message.
   */
  private void send(Denm denm, Geobroadcast destination) {
    byte[] bytes;
    try {
      bytes = UperEncoder.encode(denm);
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      logger.error("Failed to encode DENM {}, ignoring", denm, e);
      return;
    }
    BtpPacket packet = BtpPacket.customDestination(bytes, PORT_DENM, destination);
    try {
      btpSocket.send(packet);
    } catch (IOException e) {
      logger.warn("failed to send denm", e);
    }
  }

  /**
   * Broadcast a proper iCLCM message.
   *
   * @param iclcm A proper iCLCM message.
   */
  private void send(IgameCooperativeLaneChangeMessage iclcm) {
    byte[] bytes;
    try {
      bytes = UperEncoder.encode(iclcm);
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      logger.error("Failed to encode iCLCM {}, ignoring", iclcm, e);
      return;
    }
    BtpPacket packet = BtpPacket.singleHop(bytes, PORT_ICLCM, iCLCM_LIFETIME_SECONDS);
    try {
      btpSocket.send(packet);
    } catch (IOException e) {
      logger.warn("Failed to send iclcm", e);
    }
  }

  /**
   * Broadcast a custom message.
   *
   * @param buffer The byte array to send.
   */
  private void send(byte[] buffer) {
    BtpPacket packet = BtpPacket.singleHop(buffer, PORT_CUSTOM, CUSTOM_LIFETIME_SECONDS);
    try {
      btpSocket.send(packet);
    } catch (IOException e) {
      logger.warn("Failed to send custom message", e);
    }
  }

  /**
   * This class is used to provide the current position of the vehicle. The position is used by the
   * beaconing service, to generate GeoBroadcast addresses and to check if a received DENM message
   * is addressed to us.
   */
  public static class VehiclePositionProvider implements PositionProvider {
    private final Address address;
    private Position position;
    private double speedMetersPerSecond;
    private double headingDegreesFromNorth;
    private Instant lastUpdateTimestamp;

    /**
     * VehiclePositionProvider constructor.
     *
     * @param address The vehicle address.
     */
    VehiclePositionProvider(Address address) {
      this.address = address;
      this.position = new Position(0, 0);
      this.speedMetersPerSecond = 0;
      this.headingDegreesFromNorth = 0;
      this.lastUpdateTimestamp = Instant.EPOCH;
    }

    /**
     * Update the stored vehicle position.
     *
     * @param latitude The current latitude of the vehicle.
     * @param longitude The current longitude of the vehicle.
     * @param speedMetersPerSecond Vehicle speed in m/s.
     * @param headingDegreesFromNorth Heading in degrees from north.
     */
    public void update(
        double latitude,
        double longitude,
        double speedMetersPerSecond,
        double headingDegreesFromNorth) {
      this.position = new Position(latitude, longitude);
      this.speedMetersPerSecond = speedMetersPerSecond;
      this.headingDegreesFromNorth = headingDegreesFromNorth;
      this.lastUpdateTimestamp = Instant.now();
    }

    /**
     * Return the latest position of the vehicle.
     *
     * @return The latest position of the vehicle.
     */
    public Position getPosition() {
      return position;
    }

    /**
     * Get the latest position of the vehicle as a LongPositionVector.
     *
     * @return The latest position of the vehicle.
     */
    @Override
    public LongPositionVector getLatestPosition() {

      /* The position is considered confident for 200 ms after a
       * position update. */
      Instant timestamp = Instant.now();
      boolean isPositionConfident = timestamp.minusMillis(200).isBefore(lastUpdateTimestamp);

      return new LongPositionVector(
          address,
          timestamp,
          position,
          isPositionConfident,
          speedMetersPerSecond,
          headingDegreesFromNorth);
    }
  }

  /** This class is used to create a socket address from a string. */
  private static class SocketAddressFromString {
    private final InetSocketAddress address;

    /**
     * SocketAddressFromString constructor.
     *
     * @param addressStr String formatted as host:port
     */
    public SocketAddressFromString(final String addressStr) {
      String[] hostAndPort = addressStr.split(":");
      if (hostAndPort.length != 2) {
        throw new IllegalArgumentException("Expected host:port, got " + addressStr);
      }
      String hostname = hostAndPort[0];
      int port = Integer.parseInt(hostAndPort[1]);
      this.address = new InetSocketAddress(hostname, port);
    }

    /**
     * Get the address as an InetSocketAddress.
     *
     * @return InetSocketAddress address.
     */
    public InetSocketAddress asInetSocketAddress() {
      return address;
    }
  }

  /** The main method will start the router with the provided properties. */
  public static void main(String[] args) throws IOException {

    /* Load properties from file */
    Properties props = new Properties();
    FileInputStream in;
    if (args.length > 0) {
      in = new FileInputStream(args[0]);
    } else {
      in = new FileInputStream("router.properties");
    }
    props.load(in);
    in.close();

    /* Time to get the ball rolling! */
    Router router = new Router(props);
  }
}
/* That's all folks! */
