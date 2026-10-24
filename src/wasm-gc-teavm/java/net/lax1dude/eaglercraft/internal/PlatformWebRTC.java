/*
 * Copyright (c) 2024 lax1dude, ayunami2000. All Rights Reserved.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.lax1dude.eaglercraft.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.teavm.interop.Import;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.Uint8Array;

import net.lax1dude.eaglercraft.internal.buffer.WASMGCDirectArrayConverter;
import net.lax1dude.eaglercraft.internal.wasm_gc_teavm.BetterJSStringConverter;
import net.lax1dude.eaglercraft.sp.lan.LANPeerEvent;

/**
 * Treasure Client: WASM-GC binding to platformWebRTC.js, providing the LAN /
 * world-sharing WebRTC layer.
 *
 * The client-side functions map 1:1 to the JS. The server (hosting) side is
 * different: platformWebRTC.js (u2) manages peers as JS objects returned from
 * serverLANCreatePeer, whereas the ported LAN java expects a flat API keyed by
 * peer id. This class bridges the two — it keeps a Map of the JS peer objects
 * and exposes the flat serverLAN* methods our LAN java calls, translating each
 * into the peer object's methods and reconstructing typed LANPeerEvents.
 *
 * Event type constants must match platformWebRTC.js.
 */
public class PlatformWebRTC {

	private static final int EVENT_WEBRTC_ICE = 0;
	private static final int EVENT_WEBRTC_DESC = 1;
	private static final int EVENT_WEBRTC_OPEN = 2;
	private static final int EVENT_WEBRTC_PACKET = 3;
	private static final int EVENT_WEBRTC_CLOSE = 4;

	// ------------------------------------------------------------------
	// JS object shapes (returned by the JS layer)
	// ------------------------------------------------------------------

	/** An event object from a peer's nextEvent(): { type:int, data:* }. */
	public interface JSWebRTCEvent extends JSObject {
		@JSProperty("type")
		int getType();

		@JSProperty("data")
		JSObject getData();
	}

	/** A peer handle returned by serverLANCreatePeer. */
	public interface JSLANPeer extends JSObject {
		int countAvailableEvents();

		JSWebRTCEvent nextEvent();

		void writePacket(Uint8Array dat);

		void handleRemoteICECandidates(JSString iceCandidates);

		void handleRemoteDescription(JSString desc);

		void mapIPC(JSString ipcChannel);

		void disconnect();
	}

	// ------------------------------------------------------------------
	// Native imports — client side (flat, 1:1 with the JS)
	// ------------------------------------------------------------------

	@Import(module = "platformWebRTC", name = "supported")
	public static native boolean supported();

	@Import(module = "platformWebRTC", name = "clientLANReadyState")
	public static native int clientLANReadyState();

	@Import(module = "platformWebRTC", name = "clientLANCloseConnection")
	public static native void clientLANCloseConnection();

	@Import(module = "platformWebRTC", name = "clientLANSendPacket")
	private static native void clientLANSendPacket0(Uint8Array pkt);

	public static void clientLANSendPacket(byte[] pkt) {
		clientLANSendPacket0(WASMGCDirectArrayConverter.byteArrayToExternU8Array(pkt));
	}

	@Import(module = "platformWebRTC", name = "clientLANReadPacket")
	private static native Uint8Array clientLANReadPacket0();

	@Import(module = "platformWebRTC", name = "clientLANAvailable")
	public static native int clientLANAvailable();

	/** Drain all queued incoming packets (our LAN java calls this each tick). */
	public static List<byte[]> clientLANReadAllPacket() {
		List<byte[]> ret = null;
		Uint8Array a;
		while ((a = clientLANReadPacket0()) != null) {
			if (ret == null) ret = new ArrayList<>();
			ret.add(WASMGCDirectArrayConverter.externU8ArrayToByteArray(a));
		}
		return ret;
	}

	@Import(module = "platformWebRTC", name = "clientLANSetICEServersAndConnect")
	private static native void clientLANSetICEServersAndConnect0(JSArray<JSString> servers);

	public static void clientLANSetICEServersAndConnect(String[] servers) {
		clientLANSetICEServersAndConnect0(BetterJSStringConverter.stringArrayToJS(servers));
	}

	@Import(module = "platformWebRTC", name = "clearLANClientState")
	public static native void clearLANClientState();

	@Import(module = "platformWebRTC", name = "clientLANAwaitICECandidate")
	private static native JSString clientLANAwaitICECandidate0();

	public static String clientLANAwaitICECandidate() {
		JSString s = clientLANAwaitICECandidate0();
		return s == null ? null : BetterJSStringConverter.stringFromJS(s);
	}

	@Import(module = "platformWebRTC", name = "clientLANAwaitDescription")
	private static native JSString clientLANAwaitDescription0();

	public static String clientLANAwaitDescription() {
		JSString s = clientLANAwaitDescription0();
		return s == null ? null : BetterJSStringConverter.stringFromJS(s);
	}

	@Import(module = "platformWebRTC", name = "clientLANAwaitChannel")
	public static native boolean clientLANAwaitChannel();

	@Import(module = "platformWebRTC", name = "clientLANClosed")
	public static native boolean clientLANClosed();

	@Import(module = "platformWebRTC", name = "clientLANSetICECandidate")
	private static native void clientLANSetICECandidate0(JSString candidate);

	public static void clientLANSetICECandidate(String candidate) {
		clientLANSetICECandidate0(BetterJSStringConverter.stringToJS(candidate));
	}

	@Import(module = "platformWebRTC", name = "clientLANSetDescription")
	private static native void clientLANSetDescription0(JSString description);

	public static void clientLANSetDescription(String description) {
		clientLANSetDescription0(BetterJSStringConverter.stringToJS(description));
	}

	// ------------------------------------------------------------------
	// Native imports — server side (flat JS fns)
	// ------------------------------------------------------------------

	@Import(module = "platformWebRTC", name = "serverLANInitializeServer")
	private static native void serverLANInitializeServer0(JSArray<JSString> servers);

	@Import(module = "platformWebRTC", name = "serverLANCloseServer")
	private static native void serverLANCloseServer0();

	@Import(module = "platformWebRTC", name = "serverLANCreatePeer")
	private static native JSLANPeer serverLANCreatePeer0(JSString peerId);

	// ------------------------------------------------------------------
	// Server-side adapter — the flat API our LAN java expects
	// ------------------------------------------------------------------

	// live peers keyed by id (the shim over the JS peer-object model)
	private static final Map<String, JSLANPeer> serverPeers = new HashMap<>();

	public static void serverLANInitializeServer(String[] servers) {
		serverPeers.clear();
		serverLANInitializeServer0(BetterJSStringConverter.stringArrayToJS(servers));
	}

	public static void serverLANCloseServer() {
		serverPeers.clear();
		serverLANCloseServer0();
	}

	public static boolean serverLANCreatePeer(String peerId) {
		JSLANPeer peer = serverLANCreatePeer0(BetterJSStringConverter.stringToJS(peerId));
		if (peer == null) {
			return false;
		}
		serverPeers.put(peerId, peer);
		return true;
	}

	public static void serverLANPeerICECandidates(String peerId, String candidates) {
		JSLANPeer peer = serverPeers.get(peerId);
		if (peer != null) {
			peer.handleRemoteICECandidates(BetterJSStringConverter.stringToJS(candidates));
		}
	}

	public static void serverLANPeerDescription(String peerId, String description) {
		JSLANPeer peer = serverPeers.get(peerId);
		if (peer != null) {
			peer.handleRemoteDescription(BetterJSStringConverter.stringToJS(description));
		}
	}

	public static void serverLANPeerMapIPC(String peerId, String ipcChannel) {
		JSLANPeer peer = serverPeers.get(peerId);
		if (peer != null) {
			peer.mapIPC(ipcChannel == null ? null : BetterJSStringConverter.stringToJS(ipcChannel));
		}
	}

	public static void serverLANWritePacket(String peerId, byte[] data) {
		JSLANPeer peer = serverPeers.get(peerId);
		if (peer != null) {
			peer.writePacket(WASMGCDirectArrayConverter.byteArrayToExternU8Array(data));
		}
	}

	public static void serverLANDisconnectPeer(String peerId) {
		JSLANPeer peer = serverPeers.remove(peerId);
		if (peer != null) {
			try {
				peer.disconnect();
			} catch (Throwable t) {
			}
		}
	}

	public static int countPeers() {
		return serverPeers.size();
	}

	/**
	 * Drain a peer's event queue and reconstruct the typed LANPeerEvents the LAN
	 * java expects. Returns null if the peer is gone or has no events.
	 */
	public static List<LANPeerEvent> serverLANGetAllEvent(String peerId) {
		JSLANPeer peer = serverPeers.get(peerId);
		if (peer == null) {
			return null;
		}
		int count = peer.countAvailableEvents();
		if (count <= 0) {
			return null;
		}
		List<LANPeerEvent> ret = new ArrayList<>();
		for (int i = 0; i < count; ++i) {
			JSWebRTCEvent evt = peer.nextEvent();
			if (evt == null) {
				break;
			}
			int type = evt.getType();
			JSObject data = evt.getData();
			switch (type) {
			case EVENT_WEBRTC_ICE:
				ret.add(new LANPeerEvent.LANPeerICECandidateEvent(peerId,
						data == null ? null : BetterJSStringConverter.stringFromJS((JSString) data)));
				break;
			case EVENT_WEBRTC_DESC:
				ret.add(new LANPeerEvent.LANPeerDescriptionEvent(peerId,
						data == null ? null : BetterJSStringConverter.stringFromJS((JSString) data)));
				break;
			case EVENT_WEBRTC_OPEN:
				ret.add(new LANPeerEvent.LANPeerDataChannelEvent(peerId));
				break;
			case EVENT_WEBRTC_PACKET:
				ret.add(new LANPeerEvent.LANPeerPacketEvent(peerId,
						data == null ? new byte[0] : WASMGCDirectArrayConverter.externU8ArrayToByteArray((Uint8Array) data)));
				break;
			case EVENT_WEBRTC_CLOSE:
				ret.add(new LANPeerEvent.LANPeerDisconnectEvent(peerId));
				break;
			default:
				break;
			}
		}
		return ret;
	}

	/**
	 * On the teavm backend these pumped an async task queue; the WASM-GC WebRTC
	 * layer is event-driven, so this is a no-op. Kept so the LAN java compiles.
	 */
	public static void runScheduledTasks() {
	}

	/**
	 * 1.8 called this to kick off the RTC LAN server; on u2 the server is fully
	 * set up by serverLANInitializeServer, so this is a no-op kept for the API.
	 */
	public static void startRTCLANServer() {
	}

	/**
	 * 1.8 called this to init the RTC LAN client; on u2 the client is set up by
	 * clientLANSetICEServersAndConnect, so this is a no-op kept for the API.
	 */
	public static void startRTCLANClient() {
	}

}
