import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-polarblewrap-sdk' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const PolarblewrapSdk = NativeModules.PolarblewrapSdk
  ? NativeModules.PolarblewrapSdk
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

class ReactPolarblewrapSdk {
  static multiply(a: number, b: number) {
    return PolarblewrapSdk.multiply(a, b);
  }

  static connectToDevice(deviceID: string) {
    return PolarblewrapSdk.connectToDevice(deviceID);
  }

  static disconnectFromDevice(deviceID: string) {
    return PolarblewrapSdk.disconnectFromDevice(deviceID);
  }
}

export default ReactPolarblewrapSdk;
