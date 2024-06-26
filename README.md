# whipi
A minimal WHIP implementation for the Raspberry Pi. It sends Camera and Mic to a WHIP endpoint
such as 

- galene
- millicast
- twitch
- etc

###Requires a Raspberry Pi Zero,1,2,3 or 4 with a PiCam and Java 11.

It acts as a thin shim between V4l2+Alsa and webRTC - built using various opensource libraries.

## setup
(tested on bullseye lite on pi3b)


install java 11:
```
sudo apt-get install -y galternatives openjdk-11-jdk
```

Check that you have H264 encoding available:
```
v4l2-ctl --list-formats | grep H264
```
Should return
```
 	[4]: 'H264' (H.264, compressed)
```
If it does not (eg on bullseye), use raspi-config to enable 'legacy camera' and reboot, then try again.

Then test that Alsa is working (with arecord)
```
arecord -l 
```
should list your sound card/mic

## running

Start streaming like this:
```
./startStream.sh ${WHIP_URL} ${WHIP_TOKEN}
```

You'll need to get the values from the streaming service.
e.g. for Twitch they are
https://g.webrtc.live-video.net:4443/v2/offer 
and your channel key

## tweaking

You can adjust various video params in the startStreaming.sh script.
```
v4l2-ctl -L 
```
tells you possible options.


Patches/PRs/ issues welcome
Tim.

