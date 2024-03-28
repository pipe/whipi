# whipi
A minimal WHIP implementation for the Raspberry Pi. It sends Camera Mic to a WHIP endpoint.

Requires a Raspberry Pi Zero,1,2,3 or 4 with a PiCam and Java 11.

It acts as a thin shim between V4l2+Alsa and webRTC - built using various opensource libraries.

If you need to install java 11:
sudo apt-get install -y galternatives openjdk-11-jdk

Check that you have H264 encoding available:

v4l2-ctl --list-formats | grep H264

Should return
 	[4]: 'H264' (H.264, compressed)

If it does not (eg on bullseye), use raspi-config to enable 'legacy camera' and reboot, then try again.

Then test that Alsa is working (with arecord)
arecord -l 
should show your sound card/mic

Start streaming like this:

./startStream.sh ${WHIP_URL} ${WHIP_TOKEN}

You'll need to get the values from the streaming service.
e.g. for Twitch they are 
https://g.webrtc.live-video.net:4443/v2/offer 
and you channel key



Tim.

