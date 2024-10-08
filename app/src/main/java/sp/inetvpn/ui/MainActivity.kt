package sp.inetvpn.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.navigation.NavigationView
import com.tencent.mmkv.MMKV
import com.xray.lite.AppConfig
import com.xray.lite.service.V2RayServiceManager
import com.xray.lite.ui.BaseActivity
import com.xray.lite.ui.MainAngActivity
import com.xray.lite.util.MmkvManager
import com.xray.lite.util.Utils
import com.xray.lite.viewmodel.MainViewModel
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.App
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNService.setDefaultStatus
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
import sp.inetvpn.R
import sp.inetvpn.data.GlobalData
import sp.inetvpn.databinding.ActivityMainBinding
import sp.inetvpn.state.MainActivity.vpnState
import sp.inetvpn.util.CheckInternetConnection
import sp.inetvpn.util.UsageConnectionManager

/**
 * MehrabSp
 */
class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var binding: ActivityMainBinding

    private var setup: sp.inetvpn.setup.MainActivity? = null
    private var state: sp.inetvpn.state.MainActivity? = null

    private val usageConnectionManager = UsageConnectionManager()

    /**
     * openvpn service
     */
    private val isServiceRunning: Unit
        /**
         * Get service status
         */
        get() {
            setStatus(OpenVPNService.getStatus())
        }

    /**
     * v2ray storage
     */
    // MMKV
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    /**
     * v2ray register
     */
    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2Ray()
            }
        }

    /**
     * enable connection button
     */
    private var enableButtonC: Boolean = true

    // ViewModel (V2ray)
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setup = sp.inetvpn.setup.MainActivity(this, binding, mainViewModel)
        state = sp.inetvpn.state.MainActivity(this, binding, setup)

        state?.handlerSetupFirst() // set default state
        setup?.setupAll()

        initializeAll() // setup openvpn service
    }

    fun handleButtonConnect() {
        if (enableButtonC) {
            enableButtonC = false
            if (vpnState != 1) {
                when (GlobalData.defaultItemDialog) {
                    1 -> connectToOpenVpn()
                    0 -> connectToV2ray()
                }
            } else {
                when (GlobalData.defaultItemDialog) {
                    1 -> stopVpn()
                    0 -> connectToV2ray()
                }
            }
            enableButtonC = true
        } else showToast("لطفا کمی صبر کنید..")
    }

    /*
     */

    private fun connectToV2ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
            state?.setNewVpnState(0)
        } else if ((settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN") == "VPN") {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun connectToOpenVpn() {
        if (GlobalData.isStart) {
            confirmDisconnect()
        } else {
            prepareVpn()
        }
    }

    /**
     * openvpn fun
     */
    private fun initializeAll() {
        // Checking is vpn already running or not (OpenVpn)
        isServiceRunning
        VpnStatus.initLogCache(this.cacheDir)
    }

    /**
     * Stop vpn
     *
     * @return boolean: VPN status
     */
    private fun stopVpn(): Boolean {
        try {
            state?.setNewVpnState(0)
            OpenVPNThread.stop()
            return true
        } catch (e: Exception) {
            showToast("مشکلی در قطع اتصال پیش امد")
        }
        return false
    }

    /**
     * Show show disconnect confirm dialog
     */
    private fun confirmDisconnect() {
        if (GlobalData.cancelFast) {
            stopVpn()
        } else {
            val builder = AlertDialog.Builder(this)
            builder.setMessage("ایا میخواهید اتصال را قطع کنید ؟")
            builder.setPositiveButton(
                "قطع اتصال"
            ) { _, _ -> stopVpn() }

            builder.setNegativeButton(
                "لغو"
            ) { _, _ ->
                // User cancelled the dialog
            }

            // Create the AlertDialog
            val dialog = builder.create()
            dialog.show()
        }
    }

    /**
     * Prepare for vpn connect with required permission
     */
    private fun prepareVpn() {
        if (!GlobalData.isStart) {
            if (CheckInternetConnection.netCheck(this)) {
                // Checking permission for network monitor
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else startVpn() //have already permission

            } else {

                // No internet connection available
                showToast("شما به اینترنت متصل نیستید !!")
                state?.handleErrorWhenConnect()
            }
        } else if (stopVpn()) {

            // VPN is stopped, show a Toast message.
            showToast("با موفقیت قطع شد")
        }
    }

    /**
     * Taking permission for network access
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            33 -> {
                if (resultCode == Activity.RESULT_OK) {
                    // اطلاعاتی که از اکتیویتی دوم دریافت می‌کنید
                    val result = data?.getBooleanExtra("restart", false)
                    if (result == true) {
                        restartOpenVpnServer()
                    }
                    // انجام کار خاص با استفاده از callback
                }
            }

            else -> {
                if (resultCode == RESULT_OK) {

                    //Permission granted, start the VPN
                    startVpn()
                } else {
                    showToast("دسترسی رد شد !! ")
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Start the VPN
     */
    private fun startVpn() {
        usageConnectionManager.establishConnection()
//        val file = GlobalData.connectionStorage.getString("file", null)
//        val uL = GlobalData.appValStorage.decodeString("UserName", null)
//        val uU = GlobalData.appValStorage.decodeString("Password", null)

//        private final String[] ignoreOptions = {"tls-client", "allow-recursive-routing", "askpass", "auth-nocache", "up", "down", "route-up", "ipchange", "route-pre-down", "auth-user-pass-verify", "block-outside-dns", "client-cert-not-required", "dhcp-release", "dhcp-renew", "dh", "group", "ip-win32", "ifconfig-nowarn", "management-hold", "management", "management-client", "management-query-remote", "management-query-passwords", "management-query-proxy", "management-external-key", "management-forget-disconnect", "management-signal", "management-log-cache", "management-up-down", "management-client-user", "management-client-group", "pause-exit", "preresolve", "plugin", "machine-readable-output", "persist-key", "push", "register-dns", "route-delay", "route-gateway", "route-metric", "route-method", "status", "script-security", "show-net-up", "suppress-timestamps", "tap-sleep", "tmp-dir", "tun-ipv6", "topology", "user", "win-sys"};

        val file = """
key-direction 1
auth-user-pass
client
proto tcp-client
remote 185.186.51.62
port 55955
dev tun
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
verify-x509-name server_Qc4gab3Cs7LvQ2sh name
auth SHA256
auth-nocache
cipher AES-128-GCM
tls-client
tls-version-min 1.2
tls-cipher TLS-ECDHE-ECDSA-WITH-AES-128-GCM-SHA256
ignore-unknown-option block-outside-dns
setenv opt block-outside-dns # Prevent Windows 10 DNS leak
verb 3
<ca>
-----BEGIN CERTIFICATE-----
MIIB1zCCAX2gAwIBAgIUavU+CAVM6SSbiTFxUkZBprjyMY4wCgYIKoZIzj0EAwIw
HjEcMBoGA1UEAwwTY25fbmdjUUFyWjZxUDBNb1RIZDAeFw0yNDA0MDIyMTAzMDZa
Fw0zNDAzMzEyMTAzMDZaMB4xHDAaBgNVBAMME2NuX25nY1FBclo2cVAwTW9USGQw
WTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQIgDf52aQ2CkJ+GftJrgFTbOoFaaHv
n1C24bNkkZCZLDKsMTTcDWpKJ/nEYpqApPZ/9t4+rQozpwTbnM0JscRgo4GYMIGV
MAwGA1UdEwQFMAMBAf8wHQYDVR0OBBYEFH8W4lqGQEd255+laLP75gGPKhEOMFkG
A1UdIwRSMFCAFH8W4lqGQEd255+laLP75gGPKhEOoSKkIDAeMRwwGgYDVQQDDBNj
bl9uZ2NRQXJaNnFQME1vVEhkghRq9T4IBUzpJJuJMXFSRkGmuPIxjjALBgNVHQ8E
BAMCAQYwCgYIKoZIzj0EAwIDSAAwRQIhAKa8I7r2T9QWI3NpWgZwp8wtvD4a0YqF
ciT+5KFp7RkxAiAgPaXZz2XxStwUVB2jWj/4SCuB3xHSSPIc85fuqWcpoA==
-----END CERTIFICATE-----
</ca>
<cert>
-----BEGIN CERTIFICATE-----
MIIB1jCCAXugAwIBAgIQTLrIkagoN1i7/ASYQhkzpTAKBggqhkjOPQQDAjAeMRww
GgYDVQQDDBNjbl9uZ2NRQXJaNnFQME1vVEhkMB4XDTI0MDQwMjIxMDcwNVoXDTI2
MDcwNjIxMDcwNVowDjEMMAoGA1UEAwwDbW1kMFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAEK9xSxM9Ri/5wcf3a2BwFWjwhghlhwKghpHpV5+H5nlueGCIHE3+w6FqZ
c4M2J7RFyJff8ezmC/nB/4eXHlocw6OBqjCBpzAJBgNVHRMEAjAAMB0GA1UdDgQW
BBRpHr0l3Qi0IYt5RAH6wGIAS+dcXDBZBgNVHSMEUjBQgBR/FuJahkBHduefpWiz
++YBjyoRDqEipCAwHjEcMBoGA1UEAwwTY25fbmdjUUFyWjZxUDBNb1RIZIIUavU+
CAVM6SSbiTFxUkZBprjyMY4wEwYDVR0lBAwwCgYIKwYBBQUHAwIwCwYDVR0PBAQD
AgeAMAoGCCqGSM49BAMCA0kAMEYCIQDIleKm+7Auegyipu50h3OyAgCleJ0cYPk9
ytiXC/DO8wIhAP/WsHjWphBDW7KBVsXVYa+CbeH9gUfFfKwtZWD7H6Zz
-----END CERTIFICATE-----
</cert>
<key>
-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgI24ntnwYjYZ0CxNd
gB7jFIvQGijJeZUDL4n0PMst1M6hRANCAAQr3FLEz1GL/nBx/drYHAVaPCGCGWHA
qCGkelXn4fmeW54YIgcTf7DoWplzgzYntEXIl9/x7OYL+cH/h5ceWhzD
-----END PRIVATE KEY-----
</key>
<tls-crypt>
#
# 2048 bit OpenVPN static key
#
-----BEGIN OpenVPN Static key V1-----
4f47df5d26cdbdf0494a779eaaadc497
e932c4d16699c5438f5fbd9627a8500b
34c6878655962172d61036cb5faf2800
2f93b06e67b12b55fffa34e2d6a53d86
4f9d0d3a678efb7e336c79720ef5e98d
80ef629742c67fe8ceb914fb5e08c697
8f5d815167db0bf8a22952d055488d11
d41782e75100c3bd23f06ce936a5447c
fb18b240d5a93be931806cb15debb314
e1732b062a53d64f95003dcc28c17d62
0fe5c5ce62d0fa67ef34f5295a351eec
530d2f41e091259401c2737606ae9598
52e2c9e5c050fde7479cee9b14ecd23b
c24b64bcb3e57ae4a9953b420d9b4530
38e930e3c226294afd513e329f99d175
3076b0bb687d9bc6ca91308484836590
-----END OpenVPN Static key V1-----
</tls-crypt>
        """.trimIndent()


        try {
            if (file != null) {
                setup?.setNewImage()

                App.clearDisallowedPackageApplication()
                App.addArrayDisallowedPackageApplication(GlobalData.disableAppsList)

                Toast.makeText(this, "در حال اتصال ...", Toast.LENGTH_SHORT).show()
                Log.d("String", file)
                OpenVpnApi.startVpn(this, file, "Japan", "My", "My")

            } else {
                startServersActivity()
                Toast.makeText(this, "ابتدا یک سرور را انتخاب کنید", Toast.LENGTH_SHORT).show()
            }
        } catch (e: RemoteException) {
            Log.d("ERROR REMOTE", e.toString())
            showToast("Remote error!")
        }
    }

    /**
     * Status change with corresponding vpn connection status
     *
     * @param connectionState
     */
    fun setStatus(connectionState: String?) {
        if (connectionState != null) {
            when (connectionState) {
                "CONNECTING" -> state?.setNewVpnState(1)

                "DISCONNECTED" -> {
                    stopVpn()
                    setDefaultStatus()
                }

                "CONNECTED" -> {
                    state?.setNewVpnState(2)
//                    checkInformationUser(this)
                }

                "WAIT" -> state?.setNewVpnState(1)

                "AUTH" -> state?.handleAUTH()
                "AUTH_PENDING" -> state?.handleAUTH()

                "RECONNECTING" -> state?.setNewVpnState(1)
                "NONETWORK" -> state?.handleWaitWhenConnect()

                "EXITING" -> showToast("ورود ناموفق بود")
            }
        }
    }

    /**
     * Receive broadcast message
     */
    private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                setStatus(intent.getStringExtra("state"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                var duration = intent.getStringExtra("duration")
                var lastPacketReceive = intent.getStringExtra("lastPacketReceive")
                var byteIn = intent.getStringExtra("byteIn")
                var byteOut = intent.getStringExtra("byteOut")
                if (duration == null) duration = "00:00:00"
                if (lastPacketReceive == null) lastPacketReceive = "0"
                if (byteIn == null) byteIn = " "
                if (byteOut == null) byteOut = " "
                updateConnectionStatus(duration, lastPacketReceive, byteIn, byteOut)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Update status UI
     *
     * @param duration:          running time
     * @param lastPacketReceive: last packet receive time
     * @param byteIn:            incoming data
     * @param byteOut:           outgoing data
     */
    fun updateConnectionStatus(
        duration: String?,
        lastPacketReceive: String?,
        byteIn: String?,
        byteOut: String?
    ) {
//        binding.durationTv.setText("Duration: " + duration);
//        binding.lastPacketReceiveTv.setText("Packet Received: " + lastPacketReceive + " second ago");
//        binding.byteInTv.setText("Bytes In: " + byteIn);
//        binding.byteOutTv.setText("Bytes Out: " + byteOut);
    }

    /**
     * Show toast message
     *
     * @param message: toast message
     */
    fun showToast(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Restart OpenVpn
     */
    private fun restartOpenVpnServer() {
        // Stop previous connection
        if (GlobalData.isStart) {
            stopVpn()
            // Delay for start
            Handler().postDelayed({ prepareVpn() }, 500)
        }
    }

    /**
     * v2ray
     */
    // v2ray
    private fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            state?.setNewVpnState(0)
            return
        }
        // Set loader for V2ray
        state?.setNewVpnState(1)
        setup?.showCircle()
        // Start
        V2RayServiceManager.startV2Ray(this)
        usageConnectionManager.establishConnection()
        // Hide loader
        setup?.hideCircle()
        state?.setNewVpnState(2)
    }

    fun setStateFromOtherClass(newState: Int) {
        state?.setNewVpnState(newState)
    }

    fun setFooterFromOtherClass(newState: Int) {
        state?.setNewFooterState(newState)
    }

    // drawer options
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        setup?.navigationListener(item)
//        binding.drawerLayout.closeDrawer(GravityCompat.START) // bug
        return true
    }

    /**
     * Resume main activity, Set new icon server..
     */
    override fun onResume() {
        super.onResume()

        setup?.setNewImage()
        setup?.handleCountryImage()

        // Set broadcast for OpenVpn
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter("connectionState"))

        state?.restoreTodayTextTv()
    }

    // Bug
    //    override fun onPause() {
    //        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    //        super.onPause()
    //    }

    fun startServersActivity() {
        val servers = Intent(this@MainActivity, ServersActivity::class.java)
        startActivityForResult(servers, 33)
        overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left)
    }

    fun startAngActivity() {
        startActivity(Intent(this@MainActivity, MainAngActivity::class.java))
        overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            moveTaskToBack(true)
        }
    }

}
