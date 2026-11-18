package com.lnxv.player.decoders;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lets the app decide whether the software (dav1d) AV1 renderer may be used for a given video.
 *
 * <p>LNXV-specific: this class is not part of upstream Media3. The patched {@code
 * Libdav1dVideoRenderer} consults it from {@code supportsFormat} and {@code createDecoder}. When no
 * gate is installed everything is allowed (upstream behaviour).
 */
public final class Av1SoftwareGate {

  /** Software AV1 decoding may be used for this format. */
  public static final int ALLOWED = 0;
  /** The user chose "Hardware only" decoding. */
  public static final int BLOCKED_BY_MODE = 1;
  /** The video is above the resolution / frame-rate limit for software decoding. */
  public static final int EXCEEDS_LIMIT = 2;

  /** Decides for a format. Called on the playback thread; must be fast and side-effect free. */
  public interface Gate {
    int check(Format format);
  }

  /** What was refused, so the UI can explain it instead of showing a generic error. */
  public static final class Denial {
    public final int reason;
    public final int width;
    public final int height;
    public final float frameRate;

    Denial(int reason, Format format) {
      this.reason = reason;
      this.width = format.width;
      this.height = format.height;
      this.frameRate = format.frameRate;
    }
  }

  private static volatile @Nullable Gate gate;
  private static final AtomicReference<Denial> lastDenial = new AtomicReference<>();

  private Av1SoftwareGate() {}

  /** Installs the gate, or removes it with {@code null}. */
  public static void setGate(@Nullable Gate newGate) {
    gate = newGate;
  }

  /** Returns {@link #ALLOWED}, {@link #BLOCKED_BY_MODE} or {@link #EXCEEDS_LIMIT}. */
  public static int check(Format format) {
    Gate current = gate;
    return current == null ? ALLOWED : current.check(format);
  }

  /** Called by the renderer when it refuses to create a decoder. */
  public static void recordDenial(int reason, Format format) {
    lastDenial.set(new Denial(reason, format));
  }

  /** Returns and clears the most recent refusal, or null if there was none. */
  @Nullable
  public static Denial consumeDenial() {
    return lastDenial.getAndSet(null);
  }
}
