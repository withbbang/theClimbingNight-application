import { AndroidConfig, withAndroidManifest, type ConfigPlugin } from 'expo/config-plugins';

const CHANGES = ['fontScale', 'density', 'locale', 'layoutDirection'];

type Manifest = AndroidConfig.Manifest.AndroidManifest;

export function addConfigChanges(manifest: Manifest): Manifest {
  const activity = AndroidConfig.Manifest.getMainActivityOrThrow(manifest);
  const current = activity.$['android:configChanges']?.split('|') ?? [];
  activity.$['android:configChanges'] = [...new Set([...current, ...CHANGES])].join('|');
  return manifest;
}

/** 글꼴 크기·화면 배율·언어가 바뀌어도 액티비티를 다시 만들지 않는다. 다시 만들면 보던 화면과 입력이 사라진다. */
const withConfigChanges: ConfigPlugin = (config) =>
  withAndroidManifest(config, (props) => {
    props.modResults = addConfigChanges(props.modResults);
    return props;
  });

export default withConfigChanges;
