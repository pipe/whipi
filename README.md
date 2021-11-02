# whipi
A minimal WHIP implementation for the Raspberry Pi. It sends Mic and Camera to a WHIP endpoint

Requires a Raspberry Pi with a PiCam and Java 11.


First, configure your PiCam with v4l2 - You _must_ use pixelformat 4 - H264

v4l2-ctl --set-fmt-video width=1920,height=1080,pixelformat=4 -d /dev/video0 --set-ctrl video_bitrate=1500000,h264_profile=1,repeat_sequence_header=1,rotate=0,h264_i_frame_period=150

Run it like this:
java -jar target/whipi-1.0-SNAPSHOT.jar ${WHIP_URL} ${WHIP_TOKEN}


This is very much a Work in Progress. 
PRs etc are most welcome.

Tim.
