#!/bin/sh 
v4l2-ctl \
   --set-fmt-video width=640,height=480,pixelformat=4 \
   -d /dev/video0 \
   --set-ctrl video_bitrate=1500000,h264_profile=4,h264_level=9,repeat_sequence_header=1,rotate=180,h264_i_frame_period=150

exec  java -jar target/whipi-1.1.jar $1 $2
