package com.movement.wallet

import android.content.Context
import android.util.Log
import org.json.JSONObject

class WalletEngine(private val context: Context) {

    private val keyStore = WalletKeyStore(context)

    fun handle(
        id: Int,
        method: String,
        params: JSONObject,
        callback: (JSONObject?, String?) -> Unit
    ) {
        when (method) {
            "connect"                  -> handleConnect(params, callback)
            "disconnect"               -> callback(JSONObject(), null)
            "account"                  -> handleAccount(callback)
            "network"                  -> handleNetwork(callback)
            "signTransaction"          -> handleSign(params, callback)
            "signAndSubmitTransaction" -> handleSignAndSubmit(params, callback)
            "signMessage"              -> handleSignMessage(params, callback)
            else -> callback(null, "Unknown method: $method")
        }
    }

    private fun handleConnect(
        params: JSONObject,
        callback: (JSONObject?, String?) -> Unit
    ) {
        val silent = params.optBoolean("silent", false)

        if (silent && keyStore.hasAccount()) {
            val result = JSONObject().apply {
                put("account", buildAccountJson())
                put("status", "Approved")
            }
            callback(result, null)
            return
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("Movement Wallet")
            .setMessage("Izinkan DApp connect ke wallet kamu?")
            .setPositiveButton("Approve") { _, _ ->
                if (!keyStore.hasAccount()) {
                    keyStore.generateNewWallet()
                }
                val result = JSONObject().apply {
                    put("account", buildAccountJson())
                    put("status", "Approved")
                }
                callback(result, null)
            }
            .setNegativeButton("Reject") { _, _ ->
                callback(null, "User rejected")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleAccount(
        callback: (JSONObject?, String?) -> Unit
    ) {
        if (!keyStore.hasAccount()) {
            callback(null, "No account")
            return
        }
        callback(buildAccountJson(), null)
    }

    private fun handleNetwork(
        callback: (JSONObject?, String?) -> Unit
    ) {
        val result = JSONObject().apply {
            put("name", "Movement Mainnet")
            put("chainId", "177")
            put("url", "https://mainnet.movementnetwork.xyz/v1")
        }
        callback(result, null)
    }

    private fun handleSign(
        params: JSONObject,
        callback: (JSONObject?, String?) -> Unit
    ) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Movement Wallet")
            .setMessage("Sign transaksi ini?")
            .setPositiveButton("Approve") { _, _ ->
                val result = JSONObject().apply {
                    put("__type", "ed25519")
                    put("public_key", keyStore.getPublicKeyHex())
                    put("signature", "0xMOCK_SIG")
                }
                callback(result, null)
            }
            .setNegativeButton("Reject") { _, _ ->
                callback(null, "User rejected")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleSignAndSubmit(
        params: JSONObject,
        callback: (JSONObject?, String?) -> Unit
    ) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Movement Wallet")
            .setMessage("Sign & Submit transaksi ini?")
            .setPositiveButton("Approve") { _, _ ->
                val result = JSONObject().apply {
                    put("hash", "0xMOCK_TX_${System.currentTimeMillis()}")
                }
                callback(result, null)
            }
            .setNegativeButton("Reject") { _, _ ->
                callback(null, "User rejected")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleSignMessage(
        params: JSONObject,
        callback: (JSONObject?, String?) -> Unit
    ) {
        val input = params.optJSONObject("input") ?: JSONObject()
        val message = input.optString("message", "")

        android.app.AlertDialog.Builder(context)
            .setTitle("Movement Wallet")
            .setMessage("Sign pesan:\n\"$message\"")
            .setPositiveButton("Approve") { _, _ ->
                val result = JSONObject().apply {
                    put("signature", "0xMOCK_MSG_SIG")
                    put("fullMessage", "APTOS\nmessage: $message")
                }
                callback(result, null)
            }
            .setNegativeButton("Reject") { _, _ ->
                callback(null, "User rejected")
            }
            .setCancelable(false)
            .show()
    }

    private fun buildAccountJson(): JSONObject {
        return JSONObject().apply {
            put("address", keyStore.getAddress())
            put("publicKey", keyStore.getPublicKeyHex())
            put("ansName", JSONObject.NULL)
            put("minKeysRequired", 1)
        }
    }
}

class WalletKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "movement_wallet",
        Context.MODE_PRIVATE
    )

    fun hasAccount(): Boolean = prefs.contains("address")

    fun generateNewWallet() {
        prefs.edit()
            .putString("public_key", "0xMOCK_PUBKEY")
            .putString("address",
                "0x0000000000000000000000000000000000000001")
            .apply()
    }

    fun getPublicKeyHex(): String =
        prefs.getString("public_key", "") ?: ""

    fun getAddress(): String =
        prefs.getString("address", "") ?: ""
}
