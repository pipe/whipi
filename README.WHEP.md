# whipi
A minimal WHEP implementation for the Raspberry Pi. It receives _audio_only_ from a  WHEP endpoint such as 

- janus
- millicast

###Requires a Raspberry Pi Zero,1,2,3 or 4 with alsa audio working.

It acts as a thin shim between Alsa and webRTC - built using various opensource libraries.

## setup
tested on bookworm lite on pi4


install java 11:
```
sudo apt-get install -y galternatives openjdk-17-jdk
```

Test that Alsa is working (with arecord)
```
aplay -l 
```
should list your sound card/speaker
It will _only_ use whatever the default output device is for alsa

## running

Start streaming like this:
```
./startWHEP.sh ${WHEP_URL} ${WHEP_TOKEN}
```

You'll need to get the values from the streaming service.

Patches/PRs/ issues welcome
Tim.

