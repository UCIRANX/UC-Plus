package com.lumen.core.vpn.openconnect

import com.lumen.core.vpn.VpnLogBus
import org.infradead.libopenconnect.LibOpenConnect

/**
 * Engine callbacks: answers the sign-in form, protects the engine's sockets from the VPN, and
 * checks the server certificate against the pin (when one is given).
 */
class OcSession(
    private val p: OpenConnectSettings,
    private val protect: (Int) -> Boolean,
) : LibOpenConnect("OpenConnect VPN Agent (Java)") {
    private var groupApplied = false
    private var passwordUsed = false

    override fun onProgress(level: Int, msg: String?) {
        val text = msg.orEmpty().trimEnd()
        if (text.isEmpty()) return
        if (level == LibOpenConnect.PRG_ERR) VpnLogBus.warning(TAG, text) else VpnLogBus.info(TAG, text)
    }

    override fun onProtectSocket(fd: Int) {
        protect(fd)
    }

    override fun onValidatePeerCert(msg: String?): Int {
        if (p.pin.isBlank()) {
            VpnLogBus.warning(TAG, "server certificate accepted without a pin")
            return 0
        }
        return if (checkPeerCertHash(p.pin) == 0) 0 else -1
    }

    override fun onProcessAuthForm(form: LibOpenConnect.AuthForm?): Int {
        form ?: return LibOpenConnect.OC_FORM_RESULT_ERR

        val groupOpt = form.authgroupOpt
        if (groupOpt != null && p.group.isNotBlank() && !groupApplied) {
            val match = groupOpt.choices.orEmpty().firstOrNull {
                it.name.equals(p.group, ignoreCase = true) || it.label.equals(p.group, ignoreCase = true)
            }
            groupApplied = true
            if (match != null && groupOpt.value != match.name) {
                groupOpt.value = match.name
                return LibOpenConnect.OC_FORM_RESULT_NEWGROUP
            }
        }

        var unfilled = false
        for (opt in form.opts.orEmpty()) {
            if (opt.flags and LibOpenConnect.OC_FORM_OPT_IGNORE.toLong() != 0L) continue
            when (opt.type) {
                LibOpenConnect.OC_FORM_OPT_HIDDEN -> Unit
                LibOpenConnect.OC_FORM_OPT_TEXT -> if (looksLikeUsername(opt)) opt.value = p.user else unfilled = true
                LibOpenConnect.OC_FORM_OPT_PASSWORD -> if (p.password.isNotEmpty() && !passwordUsed) {
                    opt.value = p.password
                    passwordUsed = true
                } else {
                    unfilled = true
                }
                LibOpenConnect.OC_FORM_OPT_SELECT -> {
                    val match = opt.choices.orEmpty().firstOrNull {
                        it.name.equals(p.group, ignoreCase = true) || it.label.equals(p.group, ignoreCase = true)
                    }
                    if (match != null) opt.value = match.name else unfilled = true
                }
                else -> unfilled = true
            }
        }
        return if (unfilled) LibOpenConnect.OC_FORM_RESULT_ERR else LibOpenConnect.OC_FORM_RESULT_OK
    }

    private fun looksLikeUsername(opt: LibOpenConnect.FormOpt): Boolean {
        val n = (opt.name.orEmpty() + " " + opt.label.orEmpty()).lowercase()
        return n.contains("user") || n.contains("login") || n.contains("name") || n.contains("email")
    }

    private companion object {
        const val TAG = "OpenConnect"
    }
}
