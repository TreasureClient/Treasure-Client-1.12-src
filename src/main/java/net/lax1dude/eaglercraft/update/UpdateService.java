/*
 * Copyright (c) 2022-2025 lax1dude, ayunami2000. All Rights Reserved.
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

package net.lax1dude.eaglercraft.update;

/**
 * Treasure Client: minimal stub of EaglercraftX 1.8's UpdateService.
 *
 * The 1.8 relay code broadcasts and collects "client update certificates" over
 * the relay connection as an auto-update convenience feature. That feature is
 * unrelated to world sharing and its full machinery isn't present in this 1.12
 * client, so this stub lets the relay code compile while simply ignoring update
 * certificates. World sharing itself does not depend on it.
 */
public class UpdateService {

	/** No-op: this client does not participate in the relay auto-update system. */
	public static void addCertificateToSet(byte[] certificateData) {
		// intentionally does nothing — see class javadoc
	}

}
