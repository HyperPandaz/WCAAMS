import librosa
import librosa.display
import numpy as np
import scipy.signal as signal
import matplotlib.pyplot as plt
import soundfile as sf
from PIL import Image
import sys
import os

# Extend or trim signal
def extend_signal(signal, fs, target_duration):
    target_samples = int(target_duration * fs)
    original_samples = len(signal)

    if original_samples < target_samples:
        # Repeat if too short
        extended = np.tile(signal, int(np.ceil(target_samples / original_samples)))
        return extended[:target_samples]
    else:
        # Trim if too long
        return signal[:target_samples]


def bandpass_filter(audio, sr, lowcut=15, highcut=400, order=4):
    nyquist = 0.5 * sr
    low = lowcut / nyquist
    high = highcut / nyquist
    b, a = signal.butter(order, [low, high], btype='band')
    y = signal.filtfilt(b, a, audio)
    return y


# Generate and save spectrogram
def plot_MFCC(input_wav, output_img):
    y, sr = librosa.load(input_wav, sr=1000)
    y_filtered = bandpass_filter(y, sr)

    mfcc = librosa.feature.mfcc(y=y_filtered, sr=sr, n_mfcc=13)
    mfcc_db = librosa.power_to_db(mfcc, ref=np.max)

    plt.figure()
    librosa.display.specshow(mfcc_db, sr=sr)
    plt.axis('off')  # don't want axes in the image
    plt.savefig(output_img, bbox_inches='tight', pad_inches=0)
    plt.close()

def plot_waveform(input_wav, output_img):
    y, sr = librosa.load(input_wav, sr=1000)
    y_filtered = bandpass_filter(y, sr)

    # plot waveform image
    plt.figure()
    librosa.display.waveshow(y_filtered, sr=sr)
    plt.axis('off')  # don't want axes in the image
    plt.savefig(output_img, bbox_inches='tight', pad_inches=0)
    plt.close()
    # compress
    image = Image.open(output_img)
    width, height = image.size
    new_size = (width//2, height//2)
    resized_image = image.resize(new_size)
    resized_image.save(output_img, optimize=True, quality=50)


# Main function
def main(target_duration, input_wav, extended_wav, spectrogram_img, waveform_img):

    # Load with librosa
    y, sr = librosa.load(input_wav, sr=1000)
    y_extended = extend_signal(y, sr, target_duration)
    sf.write(extended_wav, y_extended, sr)  # save extended aduio 
    plot_MFCC(extended_wav, spectrogram_img)
    plot_waveform(input_wav, waveform_img)

    print("COMPLETE")
