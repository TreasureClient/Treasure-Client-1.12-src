/*
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
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

package net.lax1dude.eaglercraft.sp.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teavm.interop.Import;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.Uint8Array;

import net.lax1dude.eaglercraft.internal.IPCPacketData;
import net.lax1dude.eaglercraft.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.internal.buffer.MemoryStack;
import net.lax1dude.eaglercraft.internal.buffer.WASMGCDirectArrayConverter;
import net.lax1dude.eaglercraft.internal.wasm_gc_teavm.BetterJSStringConverter;
import net.lax1dude.eaglercraft.internal.wasm_gc_teavm.WASMGCClientConfigAdapter;
import net.lax1dude.eaglercraft.sp.server.internal.wasm_gc_teavm.JS_IPCPacketData;
import net.lax1dude.eaglercraft.sp.server.internal.wasm_gc_teavm.SingleThreadWorker;

public class ClientPlatformSingleplayer {

	private static final Logger logger = LogManager.getLogger("ClientPlatformSingleplayer");

	private static boolean isSingleThreadMode = false;

	private static final LinkedList<IPCPacketData> singleThreadMessageQueue = new LinkedList<>();

	public static void startIntegratedServer(boolean singleThreadMode) {
		singleThreadMode |= ((WASMGCClientConfigAdapter)PlatformRuntime.getClientConfigAdapter()).isSingleThreadModeTeaVM();
		if(singleThreadMode) {
			if(!isSingleThreadMode) {
				SingleThreadWorker.singleThreadStartup(singleThreadMessageQueue::add);
				isSingleThreadMode = true;
			}
		}else {
			if(!startIntegratedServer0()) {
				logger.error("Failed to start integrated server!");
				logger.error("Falling back to single thread mode...");
				startIntegratedServer(true);
			}else {
				logger.info("Integrated server started");
			}
		}
	}

	@Import(module = "clientPlatformSingleplayer", name = "startIntegratedServer")
	private static native boolean startIntegratedServer0();

	public static void sendPacket(IPCPacketData packet) {
		if(isSingleThreadMode) {
			SingleThreadWorker.sendPacketToWorker(packet);
		}else {
			MemoryStack.push();
			try {
				sendPacket0(BetterJSStringConverter.stringToJS(packet.channel),
						WASMGCDirectArrayConverter.byteArrayToStackU8Array(packet.contents));
			} finally {
				MemoryStack.pop();
			}
		}
	}

	@Import(module = "clientPlatformSingleplayer", name = "sendPacket")
	private static native void sendPacket0(JSString channel, Uint8Array arr);

	public static List<IPCPacketData> recieveAllPacket() {
		if(isSingleThreadMode) {
			if(singleThreadMessageQueue.size() == 0) {
				return null;
			}else {
				List<IPCPacketData> ret = new ArrayList<>(singleThreadMessageQueue);
				singleThreadMessageQueue.clear();
				return ret;
			}
		}else {
			int cnt = getAvailablePackets();
			if(cnt == 0) {
				return null;
			}
			IPCPacketData[] ret = new IPCPacketData[cnt];
			for(int i = 0; i < cnt; ++i) {
				ret[i] = getNextPacket().internalize();
			}
			return Arrays.asList(ret);
		}
	}

	@Import(module = "clientPlatformSingleplayer", name = "getAvailablePackets")
	private static native int getAvailablePackets();

	@Import(module = "clientPlatformSingleplayer", name = "getNextPacket")
	private static native JS_IPCPacketData getNextPacket();

	// Treasure Client: mesher worker test (.mesher). A second worker on the other CPU core, separate
	// from the singleplayer server, used to measure what moving chunk building there would cost.

	/** Starts the mesher worker; false if it couldn't be created. */
	public static boolean tcMesherStart() {
		return tcMesherStart0();
	}

	@Import(module = "clientPlatformSingleplayer", name = "tcMesherStart")
	private static native boolean tcMesherStart0();

	/** Sends bytes to the mesher worker. */
	public static void tcMesherSend(byte[] data) {
		MemoryStack.push();
		try {
			tcMesherSend0(BetterJSStringConverter.stringToJS("tcmesh"),
					WASMGCDirectArrayConverter.byteArrayToStackU8Array(data));
		} finally {
			MemoryStack.pop();
		}
	}

	@Import(module = "clientPlatformSingleplayer", name = "tcMesherSend")
	private static native void tcMesherSend0(JSString channel, Uint8Array arr);

	/** The next message from the mesher worker, or null. */
	public static byte[] tcMesherPoll() {
		if (tcMesherAvailable0() == 0) {
			return null;
		}
		return tcMesherNext0().internalize().contents;
	}

	@Import(module = "clientPlatformSingleplayer", name = "tcMesherAvailable")
	private static native int tcMesherAvailable0();

	@Import(module = "clientPlatformSingleplayer", name = "tcMesherNext")
	private static native JS_IPCPacketData tcMesherNext0();

	@Import(module = "clientPlatformSingleplayer", name = "tcMesherKill")
	public static native void tcMesherKill();

	public static boolean canKillWorker() {
		return !isSingleThreadMode;
	}

	@Import(module = "clientPlatformSingleplayer", name = "killWorker")
	public static native void killWorker();

	public static boolean isRunningSingleThreadMode() {
		return isSingleThreadMode;
	}

	public static boolean isSingleThreadModeSupported() {
		return true;
	}

	public static void updateSingleThreadMode() {
		if(isSingleThreadMode) {
			SingleThreadWorker.singleThreadUpdate();
		}
	}

	public static void showCrashReportOverlay(String report, int x, int y, int w, int h) {
		showCrashReportOverlay0(BetterJSStringConverter.stringToJS(report), x, y, w, h);
	}

	@Import(module = "clientPlatformSingleplayer", name = "showCrashReportOverlay")
	private static native void showCrashReportOverlay0(JSString report, int x, int y, int w, int h);

	@Import(module = "clientPlatformSingleplayer", name = "hideCrashReportOverlay")
	public static native void hideCrashReportOverlay();

}