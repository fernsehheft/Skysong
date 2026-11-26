# Skysong audio helper for Windows.
#
# Records what the speakers play (WASAPI loopback) and writes it to stdout as mono 16-bit
# samples at 11025 Hz. Skysong only uses it to find the beat and how loud the music is;
# the sound is never saved or sent anywhere. When Minecraft closes, the pipe breaks and
# the helper ends.

$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

namespace Skysong
{
    // The Windows audio interfaces (WASAPI), declared just as far as needed for loopback
    // capture. The methods must stay in the order the interfaces define them.

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumerator
    {
    }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
    }

    [Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioClient
    {
        int Initialize(int shareMode, int streamFlags, long bufferDuration, long periodicity, IntPtr format, IntPtr sessionGuid);
        int GetBufferSize(out uint frames);
        int GetStreamLatency(out long latency);
        int GetCurrentPadding(out uint padding);
        int IsFormatSupported(int shareMode, IntPtr format, out IntPtr closestMatch);
        int GetMixFormat(out IntPtr format);
        int GetDevicePeriod(out long defaultPeriod, out long minimumPeriod);
        int Start();
        int Stop();
        int Reset();
        int SetEventHandle(IntPtr eventHandle);
        int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
    }

    [Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioCaptureClient
    {
        int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong devicePosition, out ulong qpcPosition);
        int ReleaseBuffer(uint frames);
        int GetNextPacketSize(out uint frames);
    }

    /// Records what the speakers play (loopback) and writes it to stdout as mono 16-bit
    /// little-endian samples at the given rate. While nothing plays, Windows sends nothing,
    /// so silence is filled in to keep the stream in step with the clock.
    public static class LoopbackCapture
    {
        const int Render = 0;
        const int ConsoleRole = 0;
        const int ClsCtxAll = 23;
        const int SharedMode = 0;
        const int LoopbackFlag = 0x00020000;
        const uint SilentFlag = 0x2;
        const long BufferDuration = 2000000; // 200 ms in 100 ns units
        const ushort FormatExtensible = 0xFFFE;
        const ushort FormatFloat = 3;
        static readonly Guid FloatSubFormat = new Guid("00000003-0000-0010-8000-00AA00389B71");
        static readonly Guid AudioClientId = new Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
        static readonly Guid CaptureClientId = new Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317");

        public static void Run(int outputRate)
        {
            Stream output = Console.OpenStandardOutput();
            while (true)
            {
                try
                {
                    Capture(output, outputRate);
                }
                catch (IOException)
                {
                    // Minecraft closed the pipe: time to stop.
                    return;
                }
                catch (Exception e)
                {
                    // The output device changed or went away; start over with the new default.
                    Console.Error.WriteLine(e.Message);
                    Thread.Sleep(1000);
                }
            }
        }

        static void Check(int result, string what)
        {
            if (result < 0)
            {
                throw new Exception(what + " failed: 0x" + result.ToString("X8"));
            }
        }

        static void Capture(Stream output, int outputRate)
        {
            IMMDeviceEnumerator enumerator = (IMMDeviceEnumerator) new MMDeviceEnumerator();
            IMMDevice device;
            Check(enumerator.GetDefaultAudioEndpoint(Render, ConsoleRole, out device), "Finding the speakers");
            Guid audioClientId = AudioClientId;
            object instance;
            Check(device.Activate(ref audioClientId, ClsCtxAll, IntPtr.Zero, out instance), "Opening the speakers");
            IAudioClient client = (IAudioClient) instance;

            IntPtr format;
            Check(client.GetMixFormat(out format), "Reading the sound format");
            ushort tag = (ushort) Marshal.ReadInt16(format, 0);
            int channels = Marshal.ReadInt16(format, 2);
            int inputRate = Marshal.ReadInt32(format, 4);
            int bits = Marshal.ReadInt16(format, 14);
            bool isFloat = tag == FormatFloat;
            if (tag == FormatExtensible)
            {
                byte[] subFormat = new byte[16];
                Marshal.Copy(new IntPtr(format.ToInt64() + 24), subFormat, 0, 16);
                isFloat = new Guid(subFormat) == FloatSubFormat;
            }
            int bytesPerSample = bits / 8;
            int frameBytes = bytesPerSample * channels;

            Check(client.Initialize(SharedMode, LoopbackFlag, BufferDuration, 0, format, IntPtr.Zero), "Starting the recording");
            Marshal.FreeCoTaskMem(format);
            Guid captureClientId = CaptureClientId;
            object service;
            Check(client.GetService(ref captureClientId, out service), "Getting the recorder");
            IAudioCaptureClient capture = (IAudioCaptureClient) service;
            Check(client.Start(), "Starting the recording");

            // Turns the speakers' rate (usually 48000) into ours by averaging blocks of samples.
            double step = (double) inputRate / outputRate;
            double phase = 0;
            double sum = 0;
            int summed = 0;
            byte[] buffer = new byte[8192];
            int filled = 0;
            byte[] packet = new byte[0];
            Stopwatch clock = Stopwatch.StartNew();
            long written = 0;

            try
            {
                while (true)
                {
                    Thread.Sleep(10);
                    uint packetFrames;
                    Check(capture.GetNextPacketSize(out packetFrames), "Reading sound");
                    while (packetFrames > 0)
                    {
                        IntPtr data;
                        uint frames;
                        uint flags;
                        ulong devicePosition;
                        ulong qpcPosition;
                        Check(capture.GetBuffer(out data, out frames, out flags, out devicePosition, out qpcPosition), "Reading sound");
                        int byteCount = (int) frames * frameBytes;
                        if (packet.Length < byteCount)
                        {
                            packet = new byte[byteCount];
                        }
                        bool silent = (flags & SilentFlag) != 0;
                        if (!silent)
                        {
                            Marshal.Copy(data, packet, 0, byteCount);
                        }
                        for (int f = 0; f < frames; f++)
                        {
                            double mono = 0;
                            if (!silent)
                            {
                                for (int c = 0; c < channels; c++)
                                {
                                    int at = f * frameBytes + c * bytesPerSample;
                                    mono += isFloat ? BitConverter.ToSingle(packet, at) : BitConverter.ToInt16(packet, at) / 32768.0;
                                }
                                mono /= channels;
                            }
                            sum += mono;
                            summed++;
                            phase += 1;
                            if (phase >= step)
                            {
                                phase -= step;
                                filled = Put(output, buffer, filled, sum / summed);
                                written++;
                                sum = 0;
                                summed = 0;
                            }
                        }
                        Check(capture.ReleaseBuffer(frames), "Reading sound");
                        Check(capture.GetNextPacketSize(out packetFrames), "Reading sound");
                    }

                    // Nothing was played for a while: send silence up to now, so the time stays right.
                    long due = clock.ElapsedMilliseconds * outputRate / 1000;
                    if (due - written > outputRate / 10)
                    {
                        while (written < due)
                        {
                            filled = Put(output, buffer, filled, 0);
                            written++;
                        }
                    }
                    if (filled > 0)
                    {
                        output.Write(buffer, 0, filled);
                        output.Flush();
                        filled = 0;
                    }
                }
            }
            finally
            {
                client.Stop();
            }
        }

        static int Put(Stream output, byte[] buffer, int filled, double sample)
        {
            if (filled + 2 > buffer.Length)
            {
                output.Write(buffer, 0, filled);
                filled = 0;
            }
            int value = (int) Math.Round(Math.Max(-1.0, Math.Min(1.0, sample)) * 32767);
            buffer[filled] = (byte) (value & 0xFF);
            buffer[filled + 1] = (byte) ((value >> 8) & 0xFF);
            return filled + 2;
        }
    }
}
'@

[Skysong.LoopbackCapture]::Run(11025)
