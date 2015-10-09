package com.cerevo.blueninja.blesendmessage;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class MainActivity extends ActionBarActivity {
    //BLEスキャンタイムアウト[ms]
    private static final int SCAN_TIMEOUT = 10000;
    //接続対象のデバイス名
    private static final String DEVICE_NAME = "CDP-TZ01B_MSG";
    /* UUIDs */
    //BlueNinja Messenger Service
    private static final String UUID_SERVICE_BNMSG = "00010000-4ba2-11e5-90bc-080027cc79e0";
    //Message
    private static final String UUID_CHARACTERISTIC_BNMSG = "00010001-4ba2-11e5-90bc-080027cc79e0";

    //ログのTAG
    private static final String LOG_TAG = "BNBLE_MESSENGER";

    private BluetoothManager mBtManager;
    private BluetoothAdapter mBtAdapter;

    private Handler mHandler;

    private AppState mAppStat = AppState.INIT;

    private EditText meditMessage;
    private Button mbuttonSayHello;
    private Button mbuttonSayBye;
    private Button mbuttonSend;
    private Button mbuttonConnect;
    private Button mbuttonDisconnect;

    private BluetoothGattCharacteristic mCharacteristic;
    private BluetoothGatt mGatt;
    private BluetoothGatt mBtGatt;

    /* Application State */
    private enum AppState {
        INIT,
        BLE_SCANNING,
        BLE_SCAN_FAILED,
        BLE_DEV_FOUND,
        BLE_SRV_FOUND,
        BLE_CHARACTERISTIC_NOT_FOUND,
        BLE_CONNECTED,
        BLE_DISCONNECTED,
        BLE_SRV_NOT_FOUND,
        BLE_READ_SUCCESS,
        BLE_WRITE,
        BLE_WRITE_SUCCESS,
        BLE_WRITE_FAILED,
        BLE_CLOSED
    }

    /**
     * アプリの状態を設定
     * @param stat
     */
    private void setStatus(AppState stat)
    {
        Message msg = new Message();
        msg.what = stat.ordinal();
        msg.obj = stat.name();

        mAppStat = stat;
        mHandler.sendMessage(msg);
    }

    /**
     * アプリの状態を取得
     * @return
     */
    private AppState getStatus()
    {
        return mAppStat;
    }
    /* ----- */

    /**
     * ボタンのClickイベント
     */
    View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String msg;
            switch (v.getId()) {
                case R.id.buttonConnect:
                    connectBLE();
                    break;
                case R.id.buttonDisconnect:
                    disconnectBLE();
                    break;
                case R.id.buttonSayHello:
                    try {
                        msg = "ﾄﾞｰﾓ ﾕｰｻﾞｰ=ｻﾝ   BlueNinja ﾃﾞｽ";
                        byte byte_msg[] = msg.getBytes("SJIS");
                        mCharacteristic.setValue(byte_msg);
                        mGatt.writeCharacteristic(mCharacteristic);
                        setStatus(AppState.BLE_WRITE);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        setStatus(AppState.BLE_WRITE_FAILED);
                    }
                   break;
                case R.id.buttonSayBay:
                    try {
                        msg = "ｵﾀｯｼｬﾃﾞｰ";
                        byte byte_msg[] = msg.getBytes("SJIS");
                        mCharacteristic.setValue(byte_msg);
                        mGatt.writeCharacteristic(mCharacteristic);
                        setStatus(AppState.BLE_WRITE);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        setStatus(AppState.BLE_WRITE_FAILED);
                    }
                    break;
                case R.id.buttonSend:
                    try {
                        SpannableStringBuilder sb = (SpannableStringBuilder)meditMessage.getText();
                        msg = sb.toString();
                        byte byte_msg[] = msg.getBytes("SJIS");
                        mCharacteristic.setValue(byte_msg);
                        mGatt.writeCharacteristic(mCharacteristic);
                        setStatus(AppState.BLE_WRITE);
                        break;
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        setStatus(AppState.BLE_WRITE_FAILED);
                    }
            }
        }
    };

    /**
     * BLE接続
     */
    private void connectBLE()
    {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBtAdapter.stopLeScan(mLeScanCallback);
                if (AppState.BLE_SCANNING.equals(getStatus())) {
                    setStatus(AppState.BLE_SCAN_FAILED);
                }
            }
        }, SCAN_TIMEOUT);

        mBtAdapter.stopLeScan(mLeScanCallback);
        mBtAdapter.startLeScan(mLeScanCallback);
        setStatus(AppState.BLE_SCANNING);
    }

    /**
     * BLE切断
     */
    private void disconnectBLE()
    {
        if (mBtGatt != null) {
            mBtGatt.close();
            mBtGatt = null;
            mCharacteristic = null;
            setStatus(AppState.BLE_CLOSED);
        }
    }

    /**
     * BLEスキャンコールバック
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(LOG_TAG, String.format("Device found: %s[%s]", device.getName(), device.getUuids()));
            if (DEVICE_NAME.equals(device.getName())) {
                //BlueNinjaを発見
                setStatus(AppState.BLE_DEV_FOUND);
                mBtAdapter.stopLeScan(this);
                mBtGatt = device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback);
            }
        }
    };

    /**
     * GATTコールバック
     */
    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        /**
         * ステータス変更
         * @param gatt
         * @param status
         * @param newState
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    /* 接続 */
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    /* 切断 */
                    setStatus(AppState.BLE_DISCONNECTED);
                    mBtGatt = null;
                    break;
            }
        }

        /**
         * サービスを発見
         * @param gatt
         * @param status
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            BluetoothGattService service = gatt.getService(UUID.fromString(UUID_SERVICE_BNMSG));
            if (service == null) {
                //サービスが見つからない
                setStatus(AppState.BLE_SRV_NOT_FOUND);
            } else {
                //サービスが見つかった
                setStatus(AppState.BLE_SRV_FOUND);
                mCharacteristic = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_BNMSG));
                if (mCharacteristic == null) {
                    //Characteristicが見つからない
                    setStatus(AppState.BLE_CHARACTERISTIC_NOT_FOUND);
                    return;
                }
            }
            mGatt = gatt;
            setStatus(AppState.BLE_CONNECTED);
        }

        /**
         * Write成功
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(LOG_TAG, "onCharacteristicWrite:" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Write成功
                if (UUID_CHARACTERISTIC_BNMSG.equals(characteristic.getUuid().toString())) {
                    setStatus(AppState.BLE_WRITE_SUCCESS);
                }
            } else {
                setStatus(AppState.BLE_WRITE_FAILED);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* BLEの初期化 */
        mBtManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        if ((mBtAdapter == null) || (mBtAdapter.isEnabled() == false)) {
            Toast.makeText(getApplicationContext(), "Bluetoothが有効ではありません", Toast.LENGTH_SHORT).show();
            finish();
        }

        /* ウィジェットの初期化 */
        //EditText
        meditMessage = (EditText)findViewById(R.id.editMessage);
        //Buttons
        mbuttonSayHello = (Button)findViewById(R.id.buttonSayHello);
        mbuttonSayHello.setOnClickListener(buttonClickListener);

        mbuttonSayBye = (Button)findViewById(R.id.buttonSayBay);
        mbuttonSayBye.setOnClickListener(buttonClickListener);

        mbuttonSend = (Button)findViewById(R.id.buttonSend);
        mbuttonSend.setOnClickListener(buttonClickListener);

        mbuttonConnect = (Button)findViewById(R.id.buttonConnect);
        mbuttonConnect.setOnClickListener(buttonClickListener);

        mbuttonDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        mbuttonDisconnect.setOnClickListener(buttonClickListener);

        /* UI更新ハンドラ */
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                final TextView textStatus = (TextView)findViewById(R.id.textStatus);
                textStatus.setText((String)msg.obj);
                AppState state = AppState.values()[msg.what];
                switch (state) {
                    case BLE_CONNECTED:
                    case BLE_WRITE_SUCCESS:
                    case BLE_WRITE_FAILED:
                        mbuttonConnect.setEnabled(false);
                        mbuttonDisconnect.setEnabled(true);
                        mbuttonSayHello.setEnabled(true);
                        mbuttonSayBye.setEnabled(true);
                        mbuttonSend.setEnabled(true);
                        break;
                    case BLE_WRITE:
                        mbuttonConnect.setEnabled(false);
                        mbuttonDisconnect.setEnabled(true);
                        mbuttonSayHello.setEnabled(false);
                        mbuttonSayBye.setEnabled(false);
                        mbuttonSend.setEnabled(false);
                        break;
                    case INIT:
                    case BLE_DISCONNECTED:
                    case BLE_CLOSED:
                        mbuttonConnect.setEnabled(true);
                        mbuttonDisconnect.setEnabled(false);
                        mbuttonSayHello.setEnabled(false);
                        mbuttonSayBye.setEnabled(false);
                        mbuttonSend.setEnabled(false);
                        break;
                }
            }
        };

        setStatus(AppState.INIT);
    }
}
