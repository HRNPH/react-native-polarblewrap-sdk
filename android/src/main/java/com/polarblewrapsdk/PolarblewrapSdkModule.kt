package com.polarblewrapsdk

import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.ReactContext
import android.util.Log

import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import java.util.*

class PolarblewrapSdkModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "PolarblewrapSdk"
    private const val TAG = "MainActivity"
    private const val API_LOGGER_TAG = "API LOGGER"
    private const val PERMISSION_REQUEST_CODE = 1
    private var deviceID: String = "assa"
  }
  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  fun multiply(a: Double, b: Double, promise: Promise) {
    promise.resolve(a * b)
  }

  // polar ble sdk stuff
  private class Device {
    var hrReady: Boolean = false
    var ecgReady: Boolean = false
    var accReady: Boolean = false
    var ppgReady: Boolean = false
    var ppiReady: Boolean = false
    var broadcastDisposable: Disposable? = null
    var ecgDisposable: Disposable? = null
    var accDisposable: Disposable? = null
    var ppgDisposable: Disposable? = null
    var ppiDisposable: Disposable? = null
    var scanDisposable: Disposable? = null
    var autoConnectDisposable: Disposable? = null
    var exerciseEntry: PolarExerciseEntry? = null
  }

  private val devices: MutableMap<String, Device> = mutableMapOf()
  private var broadcastDisposable: Disposable? = null
  private var ecgDisposable: Disposable? = null
  private var accDisposable: Disposable? = null
  private var ppgDisposable: Disposable? = null
  private var ppiDisposable: Disposable? = null
  private var scanDisposable: Disposable? = null
  private var autoConnectDisposable: Disposable? = null
  private var exerciseEntry: PolarExerciseEntry? = null

  // assign api at global scope
  public val api: PolarBleApi? = null
  // intregation
  private var reactContext: ReactApplicationContext = reactContext
  public class PolarModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    override fun getName(): String {
      return "PolarModule"
    }
    
    private var reactContext: ReactApplicationContext? = null

    // public val api: PolarBleApi by lazy {
    //   // Notice all features are enabled
    //   PolarBleApiDefaultImpl.defaultImplementation(
    //     reactContext,
    //     setOf(
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
    //       PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
    //     )
    //   )
    // }
    // create api from global scope
    val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
      reactContext,
      setOf(
          PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
          PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
          PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
          PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
          PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
          PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
          PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
          PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING
      )
    )

    // start callback
    class MyApiCallback : PolarBleApiCallback() {
      override public fun blePowerStateChanged(powered: Boolean) {
        val params: WritableMap = Arguments.createMap()
        Log.d(TAG, "BLE power: $powered")
        params.putString("deviceID", deviceID)
        params.putBoolean("state", powered); // <- put boolean value true/false for the key "bluetoothEnabled"
      }

      override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
        deviceID = polarDeviceInfo.deviceId
      }

      override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
      }

      override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
          Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
      }

      override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
          Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
      }

      override fun batteryLevelReceived(identifier: String, level: Int) {
          Log.d(TAG, "BATTERY LEVEL: $level")
      }
    }
    // Usage
    init {
      api.setApiCallback(MyApiCallback())
    }
  }

  private fun emit(reactContext: ReactContext, eventName: String, params: WritableMap?) {
    if (reactContext.catalystInstance?.isDestroyed == false) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }
  }
  @ReactMethod
  public fun connectToDevie(id: String)  {
    var params:WritableMap = Arguments.createMap();
    params.putString("deviceID", id);
    params.putString("state", "scaning");
    // emit
    emit(reactContext, "deviceState", params);
    try {
      api?.connectToDevice(id)
    }
    catch (e: PolarInvalidArgument) {
      Log.d(TAG, "Invalid argument: ${e.localizedMessage}")
    }
  }

  @ReactMethod
  public fun disconnectFromDevice(id: String) {
    var params:WritableMap = Arguments.createMap();
    params.putString("deviceID", id);
    params.putString("state", "disconnected");
    // emit
    emit(reactContext, "deviceState", params);
    try {
      api?.disconnectFromDevice(id)
    }
    catch (e: PolarInvalidArgument) {
      Log.d(TAG, "Invalid argument: ${e.localizedMessage}")
    }
  }
}
