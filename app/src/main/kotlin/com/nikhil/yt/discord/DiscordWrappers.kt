package com.nikhil.yt.discord
import com.my.kizzy.gateway.entities.op.OpCode
typealias GatewayOp = OpCode
enum class GatewaySessionState { DISCONNECTED, CONNECTING, CONNECTED, RESUMING }
data class GatewayReadyEvent(val sessionId: String = "", val resumeGatewayUrl: String = "")
data class GatewayCloseInfo(val code: Int, val reason: String = "", val isResumable: Boolean = true) {
    fun copy(code: Int = this.code, reason: String = this.reason, isResumable: Boolean = this.isResumable) = GatewayCloseInfo(code, reason, isResumable)
}
object GatewayDefaults { const val HEARTBEAT_INTERVAL = 41_250L; const val IDENTIFY_INTERVAL = 5_000L }
object GatewayCapabilitiesFlags { const val MESSAGE_CONTENT_V2 = (1 shl 15) }
object IntentsFlags { const val GUILD_MESSAGES = (1 shl 9); const val MESSAGE_CONTENT = (1 shl 15) }
val NON_RESUMABLE_CLOSE_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)
const val DISCORD_APPLICATION_ID = "1284533208369975316"
const val DISCORD_APPLICATION_ID_LONG: Long = 1284533208369975316L
const val DISCORD_REDIRECT_SCHEME = "velune"
