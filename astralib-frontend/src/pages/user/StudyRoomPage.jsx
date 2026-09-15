import React, {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  useNavigate,
  useParams,
} from 'react-router-dom';

import {
  ArrowLeft,
  Camera,
  CameraOff,
  Mic,
  MicOff,
  MonitorUp,
  PhoneOff,
  Users2,
  AlertCircle,
  Loader2,
  Send,
  Wifi,
  WifiOff,
  MonitorOff,
  Circle,
} from 'lucide-react';

import { api } from '../../services/api';

// =========================================================
// REMOTE VIDEO TILE
// =========================================================

function RemoteVideoTile({
  userId,
  participant,
  stream,
}) {
  const videoRef =
    useRef(null);

  useEffect(() => {
    if (
      videoRef.current &&
      stream
    ) {
      videoRef.current.srcObject =
        stream;
    }
  }, [stream]);

  return (
    <div
      style={{
        position: 'relative',
        background: '#111827',
        borderRadius: '0.75rem',
        overflow: 'hidden',
        minHeight: '260px',
      }}
    >
      {stream ? (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          style={{
            width: '100%',
            height: '100%',
            minHeight: '260px',
            objectFit: 'cover',
            display: 'block',
          }}
        />
      ) : (
        <div
          style={{
            minHeight: '260px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column',
            gap: '0.75rem',
            color: '#ffffff',
          }}
        >
          <Users2 size={42} />

          <strong>
            {participant?.name ||
              `User ${userId}`}
          </strong>

          <span
            style={{
              fontSize: '0.8rem',
              opacity: 0.75,
            }}
          >
            Connecting video...
          </span>
        </div>
      )}

      <div
        style={{
          position: 'absolute',
          bottom: '0.75rem',
          left: '0.75rem',
          padding:
            '0.4rem 0.65rem',
          background:
            'rgba(0, 0, 0, 0.65)',
          color: '#ffffff',
          borderRadius: '0.35rem',
          fontSize: '0.8rem',
        }}
      >
        {participant?.name ||
          `User ${userId}`}
      </div>
    </div>
  );
}

// =========================================================
// STUDY ROOM
// =========================================================

export function StudyRoomPage() {
  const { groupId } =
    useParams();

  const navigate =
    useNavigate();

  // =========================================================
  // MEDIA REFS
  // =========================================================

  const localVideoRef =
    useRef(null);

  const localStreamRef =
    useRef(null);

  const screenStreamRef =
    useRef(null);

  // =========================================================
  // WEBSOCKET / WEBRTC REFS
  // =========================================================

  const socketRef =
    useRef(null);

  const peerConnectionsRef =
    useRef({});

  const pendingIceRef =
    useRef({});

  const currentUserIdRef =
    useRef(null);

  const leavingRef =
    useRef(false);

  // =========================================================
  // PAGE STATE
  // =========================================================

  const [group, setGroup] =
    useState(null);

  const [sessionState, setSessionState] =
    useState(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState('');

  // =========================================================
  // MEDIA STATE
  // =========================================================

  const [cameraError, setCameraError] =
    useState('');

  const [mediaReady, setMediaReady] =
    useState(false);

  const [
    cameraEnabled,
    setCameraEnabled,
  ] = useState(true);

  const [
    microphoneEnabled,
    setMicrophoneEnabled,
  ] = useState(true);

  const [
    screenSharing,
    setScreenSharing,
  ] = useState(false);

  // =========================================================
  // REAL-TIME STATE
  // =========================================================

  const [
    socketConnected,
    setSocketConnected,
  ] = useState(false);

  const [
    roomJoined,
    setRoomJoined,
  ] = useState(false);

  const [
    participants,
    setParticipants,
  ] = useState([]);

  const [
    remoteStreams,
    setRemoteStreams,
  ] = useState({});

  const [
    realtimeError,
    setRealtimeError,
  ] = useState('');

  // =========================================================
  // CHAT STATE
  // =========================================================

  const [
    chatMessages,
    setChatMessages,
  ] = useState([]);

  const [
    chatInput,
    setChatInput,
  ] = useState('');

  const chatEndRef =
    useRef(null);

  // =========================================================
  // INITIALIZE ROOM
  // =========================================================

  useEffect(() => {
    let cancelled = false;

    const initialize =
      async () => {

        try {
          setLoading(true);
          setError('');

          // -----------------------------------------------
          // GROUP
          // -----------------------------------------------

          const groupData =
            await api.studyGroups
              .getById(groupId);

          if (cancelled) {
            return;
          }

          setGroup(
            groupData
          );

          const allowed =
            groupData
              .joinedByCurrentUser ===
              true ||
            groupData
              .createdByCurrentUser ===
              true;

          if (!allowed) {
            throw new Error(
              'You must join this study group before entering its Study Hall room.'
            );
          }

          // -----------------------------------------------
          // SESSION
          // -----------------------------------------------

          try {
            const currentSession =
              await api.studySessions
                .getCurrent(
                  groupId
                );

            if (!cancelled) {
              setSessionState(
                currentSession
              );
            }
          } catch (sessionErr) {
            console.warn(
              'Unable to load study session:',
              sessionErr
            );
          }

          // -----------------------------------------------
          // CAMERA + MICROPHONE
          // -----------------------------------------------

          await startMedia();

          if (cancelled) {
            return;
          }

          // -----------------------------------------------
          // REAL-TIME ROOM
          // -----------------------------------------------

          connectWebSocket();

        } catch (err) {
          console.error(
            'Failed to initialize Study Hall:',
            err
          );

          if (!cancelled) {
            setError(
              err.message ||
                'Unable to open the Study Hall room.'
            );
          }

        } finally {
          if (!cancelled) {
            setLoading(false);
          }
        }
      };

    initialize();

    return () => {
      cancelled = true;

      cleanupRoom();
    };

  }, [groupId]);

  // =========================================================
  // CHAT AUTO SCROLL
  // =========================================================

  useEffect(() => {
    chatEndRef.current
      ?.scrollIntoView({
        behavior: 'smooth',
      });
  }, [chatMessages]);

  // =========================================================
  // START LOCAL MEDIA
  // =========================================================

  const startMedia =
    async () => {

      try {
        setCameraError('');

        if (
          !navigator
            .mediaDevices
            ?.getUserMedia
        ) {
          setCameraError(
            'Camera and microphone are not supported by this browser.'
          );

          return;
        }

        const stream =
          await navigator
            .mediaDevices
            .getUserMedia({
              video: true,
              audio: true,
            });

        localStreamRef.current =
          stream;

        if (
          localVideoRef.current
        ) {
          localVideoRef.current
            .srcObject =
            stream;
        }

        setCameraEnabled(
          true
        );

        setMicrophoneEnabled(
          true
        );

        setMediaReady(
          true
        );

      } catch (err) {
        console.error(
          'Media permission error:',
          err
        );

        setCameraError(
          'Camera or microphone access was denied. You can still use participants and Study Chat.'
        );

        setMediaReady(
          false
        );
      }
    };

  // =========================================================
  // WEBSOCKET CONNECTION
  // =========================================================

  const connectWebSocket =
    () => {

      const token =
        localStorage.getItem(
          'astralib_token'
        );

      if (!token) {
        setRealtimeError(
          'Authentication token was not found.'
        );

        return;
      }

      const protocol =
        window.location.protocol ===
        'https:'
          ? 'wss'
          : 'ws';

      const hostname =
        window.location.hostname;

      const socketUrl =
        `${protocol}://${hostname}:8082/ws/study` +
        `?token=${encodeURIComponent(
          token
        )}`;

      const socket =
        new WebSocket(
          socketUrl
        );

      socketRef.current =
        socket;

      socket.onopen =
        () => {

          console.log(
            'Study Hall WebSocket connected'
          );

          setSocketConnected(
            true
          );

          setRealtimeError(
            ''
          );

          socket.send(
            JSON.stringify({
              type: 'JOIN',

              groupId:
                Number(
                  groupId
                ),
            })
          );
        };

      socket.onmessage =
        async (event) => {

          try {
            const message =
              JSON.parse(
                event.data
              );

            await handleSocketMessage(
              message
            );

          } catch (err) {
            console.error(
              'Study Hall WebSocket message error:',
              err
            );
          }
        };

      socket.onerror =
        (event) => {

          console.error(
            'Study Hall WebSocket error:',
            event
          );

          setRealtimeError(
            'Unable to connect to the live Study Hall service.'
          );
        };

      socket.onclose =
        () => {

          console.log(
            'Study Hall WebSocket closed'
          );

          setSocketConnected(
            false
          );

          setRoomJoined(
            false
          );

          if (
            !leavingRef.current
          ) {
            setRealtimeError(
              'Live Study Hall connection was closed.'
            );
          }
        };
    };

  // =========================================================
  // HANDLE SOCKET MESSAGE
  // =========================================================

  const handleSocketMessage =
    async (
      message
    ) => {

      switch (
        message.type
      ) {

        // -----------------------------------------------
        // AUTHENTICATED SOCKET
        // -----------------------------------------------

        case 'CONNECTED': {

          currentUserIdRef.current =
            Number(
              message.userId
            );

          break;
        }

        // -----------------------------------------------
        // JOINED ROOM
        // -----------------------------------------------

        case 'JOINED': {

          currentUserIdRef.current =
            Number(
              message.userId
            );

          setRoomJoined(
            true
          );

          setParticipants(
            Array.isArray(
              message.participants
            )
              ? message.participants
              : []
          );

          const existingUsers =
            Array.isArray(
              message.existingUsers
            )
              ? message.existingUsers
              : [];

          /*
           * The newly joined participant creates
           * offers to everyone already inside.
           */
          for (
            const existingUserId
            of existingUsers
          ) {

            await createOfferForUser(
              Number(
                existingUserId
              )
            );
          }

          break;
        }

        // -----------------------------------------------
        // COMPLETE PARTICIPANT LIST
        // -----------------------------------------------

        case 'PARTICIPANTS': {

          setParticipants(
            Array.isArray(
              message.participants
            )
              ? message.participants
              : []
          );

          break;
        }

        // -----------------------------------------------
        // PARTICIPANT JOINED
        // -----------------------------------------------

        case 'PARTICIPANT_JOINED': {

          /*
           * No offer is created here.
           *
           * The joining participant receives
           * existingUsers and creates offers.
           */

          break;
        }

        // -----------------------------------------------
        // PARTICIPANT LEFT
        // -----------------------------------------------

        case 'PARTICIPANT_LEFT': {

          const userId =
            Number(
              message.userId
            );

          cleanupPeer(
            userId
          );

          setParticipants(
            (current) =>
              current.filter(
                (participant) =>
                  Number(
                    participant.userId
                  ) !==
                  userId
              )
          );

          break;
        }

        // -----------------------------------------------
        // WEBRTC OFFER
        // -----------------------------------------------

        case 'OFFER': {

          await handleOffer(
            Number(
              message.fromUserId
            ),
            message.payload
          );

          break;
        }

        // -----------------------------------------------
        // WEBRTC ANSWER
        // -----------------------------------------------

        case 'ANSWER': {

          await handleAnswer(
            Number(
              message.fromUserId
            ),
            message.payload
          );

          break;
        }

        // -----------------------------------------------
        // ICE CANDIDATE
        // -----------------------------------------------

        case 'ICE_CANDIDATE': {

          await handleIceCandidate(
            Number(
              message.fromUserId
            ),
            message.payload
          );

          break;
        }

        // -----------------------------------------------
        // CHAT
        // -----------------------------------------------

        case 'CHAT': {

          setChatMessages(
            (current) => [
              ...current,
              {
                id:
                  `${message.userId}-${message.sentAt}-${Math.random()}`,

                userId:
                  Number(
                    message.userId
                  ),

                name:
                  message.name ||
                  `User ${message.userId}`,

                content:
                  message.content,

                sentAt:
                  message.sentAt,
              },
            ]
          );

          break;
        }

        // -----------------------------------------------
        // SERVER ERROR
        // -----------------------------------------------

        case 'ERROR': {

          console.error(
            'Study Hall server error:',
            message.message
          );

          setRealtimeError(
            message.message ||
              'Study Hall realtime error.'
          );

          break;
        }

        default:
          break;
      }
    };

  // =========================================================
  // CREATE PEER CONNECTION
  // =========================================================

  const createPeerConnection =
    (
      remoteUserId
    ) => {

      const existing =
        peerConnectionsRef
          .current[
            remoteUserId
          ];

      if (
        existing &&
        existing
          .connectionState !==
          'closed'
      ) {
        return existing;
      }

      const peer =
        new RTCPeerConnection({
          iceServers: [
            {
              urls:
                'stun:stun.l.google.com:19302',
            },
          ],
        });

      peerConnectionsRef
        .current[
          remoteUserId
        ] = peer;

      // -----------------------------------------------
      // ADD LOCAL MEDIA
      // -----------------------------------------------

      const localStream =
        localStreamRef.current;

      if (localStream) {

        localStream
          .getTracks()
          .forEach(
            (track) => {

              peer.addTrack(
                track,
                localStream
              );
            }
          );

      } else {

        /*
         * User may have denied camera/mic.
         * Still allow them to receive remote media.
         */

        peer.addTransceiver(
          'video',
          {
            direction:
              'recvonly',
          }
        );

        peer.addTransceiver(
          'audio',
          {
            direction:
              'recvonly',
          }
        );
      }

      // -----------------------------------------------
      // REMOTE MEDIA RECEIVED
      // -----------------------------------------------

      peer.ontrack =
        (event) => {

          const stream =
            event.streams?.[0];

          if (!stream) {
            return;
          }

          setRemoteStreams(
            (current) => ({
              ...current,
              [remoteUserId]:
                stream,
            })
          );
        };

      // -----------------------------------------------
      // ICE
      // -----------------------------------------------

      peer.onicecandidate =
        (event) => {

          if (
            !event.candidate
          ) {
            return;
          }

          sendSocketMessage({
            type:
              'ICE_CANDIDATE',

            targetUserId:
              remoteUserId,

            payload:
              event.candidate
                .toJSON(),
          });
        };

      // -----------------------------------------------
      // CONNECTION STATE
      // -----------------------------------------------

      peer.onconnectionstatechange =
        () => {

          console.log(
            `Peer ${remoteUserId}:`,
            peer.connectionState
          );

          if (
            peer.connectionState ===
              'failed' ||
            peer.connectionState ===
              'closed'
          ) {
            cleanupPeer(
              remoteUserId
            );
          }
        };

      return peer;
    };

  // =========================================================
  // CREATE OFFER
  // =========================================================

  const createOfferForUser =
    async (
      remoteUserId
    ) => {

      if (
        remoteUserId ===
        currentUserIdRef.current
      ) {
        return;
      }

      try {

        const peer =
          createPeerConnection(
            remoteUserId
          );

        const offer =
          await peer.createOffer();

        await peer
          .setLocalDescription(
            offer
          );

        sendSocketMessage({
          type: 'OFFER',

          targetUserId:
            remoteUserId,

          payload:
            peer.localDescription,
        });

      } catch (err) {
        console.error(
          `Failed to create offer for user ${remoteUserId}:`,
          err
        );
      }
    };

  // =========================================================
  // RECEIVE OFFER
  // =========================================================

  const handleOffer =
    async (
      remoteUserId,
      offer
    ) => {

      try {

        const peer =
          createPeerConnection(
            remoteUserId
          );

        await peer
          .setRemoteDescription(
            new RTCSessionDescription(
              offer
            )
          );

        await flushPendingIce(
          remoteUserId,
          peer
        );

        const answer =
          await peer
            .createAnswer();

        await peer
          .setLocalDescription(
            answer
          );

        sendSocketMessage({
          type: 'ANSWER',

          targetUserId:
            remoteUserId,

          payload:
            peer.localDescription,
        });

      } catch (err) {
        console.error(
          'Failed to process WebRTC offer:',
          err
        );
      }
    };

  // =========================================================
  // RECEIVE ANSWER
  // =========================================================

  const handleAnswer =
    async (
      remoteUserId,
      answer
    ) => {

      try {

        const peer =
          peerConnectionsRef
            .current[
              remoteUserId
            ];

        if (!peer) {
          return;
        }

        await peer
          .setRemoteDescription(
            new RTCSessionDescription(
              answer
            )
          );

        await flushPendingIce(
          remoteUserId,
          peer
        );

      } catch (err) {
        console.error(
          'Failed to process WebRTC answer:',
          err
        );
      }
    };

  // =========================================================
  // ICE CANDIDATE
  // =========================================================

  const handleIceCandidate =
    async (
      remoteUserId,
      candidate
    ) => {

      if (!candidate) {
        return;
      }

      const peer =
        peerConnectionsRef
          .current[
            remoteUserId
          ];

      /*
       * ICE can arrive before SDP.
       * Queue it until remoteDescription exists.
       */
      if (
        !peer ||
        !peer.remoteDescription
      ) {

        if (
          !pendingIceRef
            .current[
              remoteUserId
            ]
        ) {
          pendingIceRef
            .current[
              remoteUserId
            ] = [];
        }

        pendingIceRef
          .current[
            remoteUserId
          ]
          .push(
            candidate
          );

        return;
      }

      try {

        await peer
          .addIceCandidate(
            new RTCIceCandidate(
              candidate
            )
          );

      } catch (err) {
        console.error(
          'Failed to add ICE candidate:',
          err
        );
      }
    };

  // =========================================================
  // FLUSH QUEUED ICE
  // =========================================================

  const flushPendingIce =
    async (
      remoteUserId,
      peer
    ) => {

      const queue =
        pendingIceRef.current[
          remoteUserId
        ] || [];

      for (
        const candidate
        of queue
      ) {

        try {

          await peer
            .addIceCandidate(
              new RTCIceCandidate(
                candidate
              )
            );

        } catch (err) {
          console.error(
            'Queued ICE candidate failed:',
            err
          );
        }
      }

      pendingIceRef.current[
        remoteUserId
      ] = [];
    };

  // =========================================================
  // SEND WEBSOCKET MESSAGE
  // =========================================================

  const sendSocketMessage =
    (
      message
    ) => {

      const socket =
        socketRef.current;

      if (
        !socket ||
        socket.readyState !==
          WebSocket.OPEN
      ) {
        return false;
      }

      socket.send(
        JSON.stringify(
          message
        )
      );

      return true;
    };

  // =========================================================
  // SEND CHAT
  // =========================================================

  const handleSendChat =
    (
      event
    ) => {

      event.preventDefault();

      const content =
        chatInput.trim();

      if (!content) {
        return;
      }

      const sent =
        sendSocketMessage({
          type: 'CHAT',
          content,
        });

      if (sent) {
        setChatInput('');
      }
    };

  // =========================================================
  // CAMERA
  // =========================================================

  const toggleCamera =
    () => {

      if (
        !localStreamRef.current ||
        screenSharing
      ) {
        return;
      }

      const videoTracks =
        localStreamRef.current
          .getVideoTracks();

      if (
        videoTracks.length ===
        0
      ) {
        return;
      }

      const newValue =
        !cameraEnabled;

      videoTracks.forEach(
        (track) => {
          track.enabled =
            newValue;
        }
      );

      setCameraEnabled(
        newValue
      );
    };

  // =========================================================
  // MICROPHONE
  // =========================================================

  const toggleMicrophone =
    () => {

      if (
        !localStreamRef.current
      ) {
        return;
      }

      const audioTracks =
        localStreamRef.current
          .getAudioTracks();

      if (
        audioTracks.length ===
        0
      ) {
        return;
      }

      const newValue =
        !microphoneEnabled;

      audioTracks.forEach(
        (track) => {
          track.enabled =
            newValue;
        }
      );

      setMicrophoneEnabled(
        newValue
      );
    };

  // =========================================================
  // SCREEN SHARE
  // =========================================================

  const startScreenShare =
    async () => {

      try {

        if (
          !navigator
            .mediaDevices
            ?.getDisplayMedia
        ) {
          throw new Error(
            'Screen sharing is not supported by this browser.'
          );
        }

        const displayStream =
          await navigator
            .mediaDevices
            .getDisplayMedia({
              video: true,
              audio: false,
            });

        const screenTrack =
          displayStream
            .getVideoTracks()[0];

        if (!screenTrack) {
          return;
        }

        screenStreamRef.current =
          displayStream;

        // -----------------------------------------------
        // REPLACE CAMERA TRACK FOR ALL REMOTE USERS
        // -----------------------------------------------

        const peers =
          Object.values(
            peerConnectionsRef
              .current
          );

        for (
          const peer of peers
        ) {

          const sender =
            peer
              .getSenders()
              .find(
                (item) =>
                  item.track?.kind ===
                  'video'
              );

          if (sender) {
            await sender
              .replaceTrack(
                screenTrack
              );
          }
        }

        // -----------------------------------------------
        // LOCAL PREVIEW BECOMES SHARED SCREEN
        // -----------------------------------------------

        if (
          localVideoRef.current
        ) {
          localVideoRef.current
            .srcObject =
            displayStream;
        }

        setScreenSharing(
          true
        );

        /*
         * Browser's native "Stop sharing"
         * button also returns to camera.
         */
        screenTrack.onended =
          () => {
            stopScreenShare();
          };

      } catch (err) {

        if (
          err.name !==
          'NotAllowedError'
        ) {
          console.error(
            'Screen share error:',
            err
          );

          setRealtimeError(
            err.message ||
              'Unable to share screen.'
          );
        }
      }
    };

  // =========================================================
  // STOP SCREEN SHARE
  // =========================================================

  const stopScreenShare =
    async () => {

      const displayStream =
        screenStreamRef.current;

      if (
        displayStream
      ) {
        displayStream
          .getTracks()
          .forEach(
            (track) => {
              track.stop();
            }
          );
      }

      screenStreamRef.current =
        null;

      const cameraTrack =
        localStreamRef.current
          ?.getVideoTracks()?.[0];

      if (cameraTrack) {

        const peers =
          Object.values(
            peerConnectionsRef
              .current
          );

        for (
          const peer of peers
        ) {

          const sender =
            peer
              .getSenders()
              .find(
                (item) =>
                  item.track?.kind ===
                  'video'
              );

          if (sender) {
            try {
              await sender
                .replaceTrack(
                  cameraTrack
                );
            } catch (err) {
              console.error(
                'Unable to restore camera track:',
                err
              );
            }
          }
        }
      }

      if (
        localVideoRef.current
      ) {
        localVideoRef.current
          .srcObject =
          localStreamRef.current;
      }

      setScreenSharing(
        false
      );
    };

  // =========================================================
  // END SESSION
  // =========================================================

  const handleEndSession =
    async () => {

      const liveSession =
        sessionState
          ?.liveSession;

      if (
        !liveSession
      ) {
        return;
      }

      const confirmed =
        window.confirm(
          'End the current study session?'
        );

      if (!confirmed) {
        return;
      }

      try {

        await api.studySessions
          .end(
            liveSession.id
          );

        const updated =
          await api.studySessions
            .getCurrent(
              groupId
            );

        setSessionState(
          updated
        );

      } catch (err) {
        setRealtimeError(
          err.message ||
            'Unable to end the session.'
        );
      }
    };

  // =========================================================
  // CLEAN ONE PEER
  // =========================================================

  const cleanupPeer =
    (
      remoteUserId
    ) => {

      const peer =
        peerConnectionsRef
          .current[
            remoteUserId
          ];

      if (peer) {

        try {
          peer.ontrack =
            null;

          peer.onicecandidate =
            null;

          peer.close();

        } catch {
          // Ignore cleanup failure.
        }

        delete peerConnectionsRef
          .current[
            remoteUserId
          ];
      }

      delete pendingIceRef
        .current[
          remoteUserId
        ];

      setRemoteStreams(
        (current) => {

          const updated = {
            ...current,
          };

          delete updated[
            remoteUserId
          ];

          return updated;
        }
      );
    };

  // =========================================================
  // CLEAN ENTIRE ROOM
  // =========================================================

  const cleanupRoom =
    () => {

      leavingRef.current =
        true;

      const socket =
        socketRef.current;

      if (
        socket &&
        socket.readyState ===
          WebSocket.OPEN
      ) {

        try {
          socket.send(
            JSON.stringify({
              type: 'LEAVE',
            })
          );
        } catch {
          // Ignore.
        }
      }

      if (socket) {

        try {
          socket.close();
        } catch {
          // Ignore.
        }
      }

      socketRef.current =
        null;

      Object.keys(
        peerConnectionsRef
          .current
      )
        .forEach(
          (userId) => {

            const peer =
              peerConnectionsRef
                .current[
                  userId
                ];

            try {
              peer?.close();
            } catch {
              // Ignore.
            }
          }
        );

      peerConnectionsRef.current =
        {};

      pendingIceRef.current =
        {};

      if (
        screenStreamRef.current
      ) {
        screenStreamRef.current
          .getTracks()
          .forEach(
            (track) =>
              track.stop()
          );
      }

      screenStreamRef.current =
        null;

      if (
        localStreamRef.current
      ) {

        localStreamRef.current
          .getTracks()
          .forEach(
            (track) =>
              track.stop()
          );
      }

      localStreamRef.current =
        null;
    };

  // =========================================================
  // LEAVE ROOM
  // =========================================================

  const handleLeaveRoom =
    () => {

      cleanupRoom();

      navigate(
        '/study-hall'
      );
    };

  // =========================================================
  // DATE / TIME
  // =========================================================

  const formatTime =
    (
      value
    ) => {

      if (!value) {
        return '';
      }

      const date =
        new Date(
          value
        );

      if (
        Number.isNaN(
          date.getTime()
        )
      ) {
        return value;
      }

      return date
        .toLocaleTimeString(
          [],
          {
            hour: '2-digit',
            minute:
              '2-digit',
          }
        );
    };

  // =========================================================
  // LOADING
  // =========================================================

  if (loading) {
    return (
      <div
        className="page-container"
        style={{
          minHeight: '70vh',
          display: 'flex',
          justifyContent:
            'center',
          alignItems:
            'center',
          gap: '0.75rem',
        }}
      >
        <Loader2
          size={22}
        />

        Connecting to Study Hall...
      </div>
    );
  }

  // =========================================================
  // ACCESS ERROR
  // =========================================================

  if (error) {
    return (
      <div className="page-container">

        <div
          className="card"
          style={{
            maxWidth: '700px',
            margin:
              '3rem auto',
            padding: '2rem',
          }}
        >

          <AlertCircle
            size={24}
          />

          <h2>
            Unable to Enter Study Hall
          </h2>

          <p
            style={{
              color:
                'var(--text-secondary)',
            }}
          >
            {error}
          </p>

          <button
            type="button"
            className="btn btn-primary"
            onClick={() =>
              navigate(
                '/study-hall'
              )
            }
          >
            <ArrowLeft
              size={16}
            />

            Return to Study Hall
          </button>

        </div>
      </div>
    );
  }

  const remoteParticipants =
    participants.filter(
      (participant) =>
        Number(
          participant.userId
        ) !==
        currentUserIdRef.current
    );

  // =========================================================
  // PAGE
  // =========================================================

  return (
    <div className="page-container">

      {/* =====================================================
          HEADER
      ====================================================== */}

      <div
        className="page-header"
        style={{
          display: 'flex',
          justifyContent:
            'space-between',
          alignItems:
            'flex-start',
          gap: '1rem',
          flexWrap: 'wrap',
        }}
      >

        <div className="page-title-group">

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={
              handleLeaveRoom
            }
            style={{
              marginBottom:
                '1rem',
            }}
          >
            <ArrowLeft
              size={15}
            />

            Study Hall
          </button>

          <div
            style={{
              display: 'flex',
              gap: '0.75rem',
              alignItems:
                'center',
              flexWrap: 'wrap',
            }}
          >
            <h1
              style={{
                margin: 0,
              }}
            >
              {group?.name}
            </h1>

            {sessionState?.live ? (
              <span
                style={{
                  display:
                    'inline-flex',
                  alignItems:
                    'center',
                  gap: '0.4rem',
                  padding:
                    '0.35rem 0.65rem',
                  borderRadius:
                    '999px',
                  background:
                    '#fee2e2',
                  color:
                    '#b91c1c',
                  fontSize:
                    '0.78rem',
                  fontWeight:
                    700,
                }}
              >
                <Circle
                  size={9}
                  fill="currentColor"
                />

                LIVE
              </span>
            ) : (
              <span
                style={{
                  padding:
                    '0.35rem 0.65rem',
                  borderRadius:
                    '999px',
                  background:
                    '#f3f4f6',
                  fontSize:
                    '0.78rem',
                }}
              >
                Room Open
              </span>
            )}
          </div>

          <p>
            {sessionState
              ?.liveSession
              ?.title ||
              'Live academic study room'}
          </p>

          {sessionState
            ?.liveSession
            ?.agenda && (
            <div
              style={{
                fontSize:
                  '0.82rem',
                color:
                  'var(--text-muted)',
                marginTop:
                  '0.35rem',
              }}
            >
              {
                sessionState
                  .liveSession
                  .agenda
              }
            </div>
          )}
        </div>

        <div
          style={{
            display: 'flex',
            alignItems:
              'center',
            gap: '1rem',
            flexWrap: 'wrap',
          }}
        >

          <span
            style={{
              display: 'flex',
              alignItems:
                'center',
              gap: '0.4rem',
            }}
          >
            {socketConnected ? (
              <Wifi
                size={17}
              />
            ) : (
              <WifiOff
                size={17}
              />
            )}

            {roomJoined
              ? 'Connected'
              : 'Connecting'}
          </span>

          <span
            style={{
              display: 'flex',
              alignItems:
                'center',
              gap: '0.4rem',
            }}
          >
            <Users2
              size={18}
            />

            {
              participants.length
            } online
          </span>

          {group
            ?.createdByCurrentUser &&
            sessionState?.live &&
            sessionState
              ?.liveSession && (

            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={
                handleEndSession
              }
            >
              End Session
            </button>
          )}
        </div>

      </div>

      {/* =====================================================
          REALTIME ERROR
      ====================================================== */}

      {realtimeError && (
        <div
          className="banner banner-warning"
          style={{
            marginBottom:
              '1rem',
          }}
        >
          <AlertCircle
            size={18}
          />

          <span>
            {realtimeError}
          </span>
        </div>
      )}

      {cameraError && (
        <div
          className="banner banner-warning"
          style={{
            marginBottom:
              '1rem',
          }}
        >
          <CameraOff
            size={18}
          />

          <span>
            {cameraError}
          </span>
        </div>
      )}

      {/* =====================================================
          MAIN ROOM
      ====================================================== */}

      <div
        style={{
          display: 'grid',

          gridTemplateColumns:
            'minmax(0, 3fr) minmax(300px, 1fr)',

          gap: '1.25rem',
        }}
      >

        {/* ===================================================
            VIDEO SIDE
        ==================================================== */}

        <div>

          {/* VIDEO GRID */}

          <div
            className="card"
            style={{
              padding: '1rem',
              background:
                '#0f172a',
            }}
          >

            <div
              style={{
                display: 'grid',

                gridTemplateColumns:
                  remoteParticipants.length ===
                  0
                    ? '1fr'
                    : 'repeat(auto-fit, minmax(280px, 1fr))',

                gap: '0.85rem',
              }}
            >

              {/* LOCAL VIDEO */}

              <div
                style={{
                  position:
                    'relative',

                  background:
                    '#111827',

                  borderRadius:
                    '0.75rem',

                  overflow:
                    'hidden',

                  minHeight:
                    remoteParticipants
                      .length ===
                    0
                      ? '520px'
                      : '260px',
                }}
              >

                {mediaReady ? (

                  <video
                    ref={
                      localVideoRef
                    }
                    autoPlay
                    playsInline
                    muted

                    style={{
                      width:
                        '100%',

                      height:
                        '100%',

                      minHeight:
                        remoteParticipants
                          .length ===
                        0
                          ? '520px'
                          : '260px',

                      objectFit:
                        screenSharing
                          ? 'contain'
                          : 'cover',

                      display:
                        cameraEnabled ||
                        screenSharing
                          ? 'block'
                          : 'none',
                    }}
                  />

                ) : (

                  <div
                    style={{
                      minHeight:
                        remoteParticipants
                          .length ===
                        0
                          ? '520px'
                          : '260px',

                      display:
                        'flex',

                      alignItems:
                        'center',

                      justifyContent:
                        'center',

                      flexDirection:
                        'column',

                      gap:
                        '0.8rem',

                      color:
                        '#ffffff',
                    }}
                  >
                    <CameraOff
                      size={46}
                    />

                    Camera unavailable
                  </div>
                )}

                {!cameraEnabled &&
                  !screenSharing &&
                  mediaReady && (

                  <div
                    style={{
                      position:
                        'absolute',

                      inset: 0,

                      background:
                        '#111827',

                      color:
                        '#ffffff',

                      display:
                        'flex',

                      justifyContent:
                        'center',

                      alignItems:
                        'center',

                      flexDirection:
                        'column',

                      gap:
                        '0.75rem',
                    }}
                  >
                    <CameraOff
                      size={44}
                    />

                    Camera Off
                  </div>
                )}

                <div
                  style={{
                    position:
                      'absolute',

                    bottom:
                      '0.75rem',

                    left:
                      '0.75rem',

                    color:
                      '#ffffff',

                    background:
                      'rgba(0,0,0,0.65)',

                    padding:
                      '0.4rem 0.65rem',

                    borderRadius:
                      '0.35rem',

                    fontSize:
                      '0.8rem',
                  }}
                >
                  You
                  {screenSharing
                    ? ' • presenting'
                    : ''}
                </div>

              </div>

              {/* REMOTE VIDEOS */}

              {remoteParticipants.map(
                (
                  participant
                ) => (

                  <RemoteVideoTile
                    key={
                      participant.userId
                    }

                    userId={
                      participant.userId
                    }

                    participant={
                      participant
                    }

                    stream={
                      remoteStreams[
                        participant
                          .userId
                      ]
                    }
                  />
                )
              )}

            </div>

          </div>

          {/* =================================================
              CALL CONTROLS
          ================================================== */}

          <div
            className="card"
            style={{
              marginTop:
                '1rem',

              display:
                'flex',

              justifyContent:
                'center',

              gap:
                '0.75rem',

              padding:
                '1rem',

              flexWrap:
                'wrap',
            }}
          >

            <button
              type="button"

              className={
                microphoneEnabled
                  ? 'btn btn-secondary'
                  : 'btn btn-primary'
              }

              onClick={
                toggleMicrophone
              }

              disabled={
                !mediaReady
              }
            >
              {microphoneEnabled ? (
                <Mic
                  size={18}
                />
              ) : (
                <MicOff
                  size={18}
                />
              )}

              {microphoneEnabled
                ? 'Mute'
                : 'Unmute'}
            </button>

            <button
              type="button"

              className={
                cameraEnabled
                  ? 'btn btn-secondary'
                  : 'btn btn-primary'
              }

              onClick={
                toggleCamera
              }

              disabled={
                !mediaReady ||
                screenSharing
              }
            >
              {cameraEnabled ? (
                <Camera
                  size={18}
                />
              ) : (
                <CameraOff
                  size={18}
                />
              )}

              {cameraEnabled
                ? 'Camera Off'
                : 'Camera On'}
            </button>

            {!screenSharing ? (

              <button
                type="button"
                className="btn btn-secondary"

                onClick={
                  startScreenShare
                }
              >
                <MonitorUp
                  size={18}
                />

                Share Screen
              </button>

            ) : (

              <button
                type="button"
                className="btn btn-primary"

                onClick={
                  stopScreenShare
                }
              >
                <MonitorOff
                  size={18}
                />

                Stop Sharing
              </button>
            )}

            <button
              type="button"

              className="btn btn-danger"

              onClick={
                handleLeaveRoom
              }
            >
              <PhoneOff
                size={18}
              />

              Leave Room
            </button>

          </div>

        </div>

        {/* ===================================================
            RIGHT PANEL
        ==================================================== */}

        <div>

          {/* PARTICIPANTS */}

          <div
            className="card"
            style={{
              marginBottom:
                '1rem',
            }}
          >

            <div
              style={{
                display:
                  'flex',

                justifyContent:
                  'space-between',

                alignItems:
                  'center',
              }}
            >
              <h3
                className="card-title"
              >
                Participants
              </h3>

              <span
                style={{
                  fontSize:
                    '0.78rem',

                  color:
                    'var(--text-muted)',
                }}
              >
                {
                  participants
                    .length
                } online
              </span>
            </div>

            <div
              style={{
                marginTop:
                  '1rem',

                display:
                  'flex',

                flexDirection:
                  'column',

                gap:
                  '0.7rem',
              }}
            >

              {participants.length ===
              0 ? (

                <div
                  style={{
                    color:
                      'var(--text-muted)',

                    fontSize:
                      '0.82rem',
                  }}
                >
                  Waiting for room participants...
                </div>

              ) : (

                participants.map(
                  (
                    participant
                  ) => {

                    const isMe =
                      Number(
                        participant
                          .userId
                      ) ===
                      currentUserIdRef
                        .current;

                    return (
                      <div
                        key={
                          participant
                            .userId
                        }

                        style={{
                          display:
                            'flex',

                          alignItems:
                            'center',

                          justifyContent:
                            'space-between',

                          gap:
                            '0.75rem',

                          padding:
                            '0.55rem 0',

                          borderBottom:
                            '1px solid var(--border-light)',
                        }}
                      >

                        <div
                          style={{
                            display:
                              'flex',

                            alignItems:
                              'center',

                            gap:
                              '0.6rem',
                          }}
                        >
                          <span
                            style={{
                              width:
                                '9px',

                              height:
                                '9px',

                              borderRadius:
                                '50%',

                              background:
                                '#16a34a',
                            }}
                          />

                          <div>
                            <strong
                              style={{
                                fontSize:
                                  '0.84rem',
                              }}
                            >
                              {
                                participant
                                  .name
                              }

                              {isMe
                                ? ' (You)'
                                : ''}
                            </strong>

                            <div
                              style={{
                                fontSize:
                                  '0.72rem',

                                color:
                                  'var(--text-muted)',
                              }}
                            >
                              {
                                participant
                                  .role ||
                                'USER'
                              }
                            </div>
                          </div>
                        </div>

                      </div>
                    );
                  }
                )
              )}

            </div>
          </div>

          {/* =================================================
              STUDY CHAT
          ================================================== */}

          <div
            className="card"
            style={{
              display:
                'flex',

              flexDirection:
                'column',

              height:
                '470px',
            }}
          >

            <div
              style={{
                display:
                  'flex',

                justifyContent:
                  'space-between',

                alignItems:
                  'center',

                marginBottom:
                  '0.75rem',
              }}
            >
              <h3
                className="card-title"
              >
                Study Chat
              </h3>

              <span
                style={{
                  fontSize:
                    '0.72rem',

                  color:
                    socketConnected
                      ? '#15803d'
                      : 'var(--text-muted)',
                }}
              >
                {socketConnected
                  ? 'Live'
                  : 'Offline'}
              </span>
            </div>

            {/* MESSAGES */}

            <div
              style={{
                flex: 1,

                overflowY:
                  'auto',

                padding:
                  '0.5rem',

                border:
                  '1px solid var(--border-light)',

                borderRadius:
                  '0.5rem',

                background:
                  '#fafafa',
              }}
            >

              {chatMessages.length ===
              0 ? (

                <div
                  style={{
                    height:
                      '100%',

                    display:
                      'flex',

                    alignItems:
                      'center',

                    justifyContent:
                      'center',

                    textAlign:
                      'center',

                    color:
                      'var(--text-muted)',

                    fontSize:
                      '0.8rem',

                    padding:
                      '1rem',
                  }}
                >
                  Start the group discussion.
                </div>

              ) : (

                chatMessages.map(
                  (
                    message
                  ) => {

                    const mine =
                      message.userId ===
                      currentUserIdRef
                        .current;

                    return (
                      <div
                        key={
                          message.id
                        }

                        style={{
                          display:
                            'flex',

                          justifyContent:
                            mine
                              ? 'flex-end'
                              : 'flex-start',

                          marginBottom:
                            '0.75rem',
                        }}
                      >
                        <div
                          style={{
                            maxWidth:
                              '85%',

                            padding:
                              '0.6rem 0.75rem',

                            borderRadius:
                              '0.6rem',

                            background:
                              mine
                                ? 'var(--primary-ink)'
                                : '#ffffff',

                            color:
                              mine
                                ? '#ffffff'
                                : 'var(--text-primary)',

                            border:
                              mine
                                ? 'none'
                                : '1px solid var(--border-light)',
                          }}
                        >

                          <div
                            style={{
                              fontSize:
                                '0.7rem',

                              fontWeight:
                                700,

                              marginBottom:
                                '0.25rem',

                              opacity:
                                0.8,
                            }}
                          >
                            {mine
                              ? 'You'
                              : message.name}
                          </div>

                          <div
                            style={{
                              fontSize:
                                '0.82rem',

                              lineHeight:
                                1.45,

                              wordBreak:
                                'break-word',
                            }}
                          >
                            {
                              message.content
                            }
                          </div>

                          <div
                            style={{
                              fontSize:
                                '0.64rem',

                              marginTop:
                                '0.3rem',

                              opacity:
                                0.65,

                              textAlign:
                                'right',
                            }}
                          >
                            {formatTime(
                              message.sentAt
                            )}
                          </div>

                        </div>
                      </div>
                    );
                  }
                )
              )}

              <div
                ref={
                  chatEndRef
                }
              />

            </div>

            {/* CHAT INPUT */}

            <form
              onSubmit={
                handleSendChat
              }

              style={{
                display:
                  'flex',

                gap:
                  '0.5rem',

                marginTop:
                  '0.75rem',
              }}
            >
              <input
                type="text"

                className="form-control"

                value={
                  chatInput
                }

                onChange={(e) =>
                  setChatInput(
                    e.target.value
                  )
                }

                placeholder="Message the group..."

                disabled={
                  !roomJoined
                }
              />

              <button
                type="submit"

                className="btn btn-primary"

                disabled={
                  !roomJoined ||
                  !chatInput.trim()
                }
              >
                <Send
                  size={16}
                />
              </button>
            </form>

          </div>

        </div>

      </div>

    </div>
  );
}

export default StudyRoomPage;