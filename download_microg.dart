import 'dart:io';

void main() async {
  print('Downloading MicroG APK...');
  var url = 'https://github.com/microg/GmsCore/releases/download/v0.3.3.240913/com.google.android.gms-240913009.apk';
  var request = await HttpClient().getUrl(Uri.parse(url));
  var response = await request.close();
  if (response.statusCode == 200) {
    var file = File('android/app/src/main/assets/microg.apk');
    await file.parent.create(recursive: true);
    await response.pipe(file.openWrite());
    print('Download complete: ${await file.length()} bytes');
  } else {
    print('Download failed: ${response.statusCode}');
  }
}
