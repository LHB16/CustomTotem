import React, { useEffect, useRef, useState } from 'react';

/**
 * AnimatedTotemPreview
 * Supports both static textures and vertical strip animated textures.
 */
export default function AnimatedTotemPreview({
  imageUrl,
  isAnimated = false,
  frameCount = 1,
  frameRate = 10,
  className = "",
  glow = false
}) {
  const canvasRef = useRef(null);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);
  const imageObjRef = useRef(null);
  const frameRef = useRef(0);
  const lastTimeRef = useRef(0);
  const animIdRef = useRef(null);

  useEffect(() => {
    setLoaded(false);
    setError(false);
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.src = imageUrl;

    img.onload = () => {
      imageObjRef.current = img;
      setLoaded(true);
    };

    img.onerror = () => {
      setError(true);
    };

    return () => {
      if (animIdRef.current) {
        cancelAnimationFrame(animIdRef.current);
      }
    };
  }, [imageUrl]);

  useEffect(() => {
    if (!loaded || !imageObjRef.current || !canvasRef.current) return;

    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const img = imageObjRef.current;
    const totalFrames = Math.max(1, isAnimated ? frameCount : 1);
    const frameWidth = img.width;
    const frameHeight = Math.floor(img.height / totalFrames);

    canvas.width = frameWidth;
    canvas.height = frameHeight;
    ctx.imageSmoothingEnabled = false;

    frameRef.current = 0;
    lastTimeRef.current = performance.now();
    const frameDuration = 1000 / Math.max(1, frameRate);

    const render = (time) => {
      if (isAnimated && totalFrames > 1) {
        const delta = time - lastTimeRef.current;
        if (delta >= frameDuration) {
          const framesToAdvance = Math.floor(delta / frameDuration);
          frameRef.current = (frameRef.current + framesToAdvance) % totalFrames;
          lastTimeRef.current = time - (delta % frameDuration);
        }
      }

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const sy = frameRef.current * frameHeight;
      ctx.drawImage(img, 0, sy, frameWidth, frameHeight, 0, 0, frameWidth, frameHeight);

      animIdRef.current = requestAnimationFrame(render);
    };

    animIdRef.current = requestAnimationFrame(render);

    return () => {
      if (animIdRef.current) {
        cancelAnimationFrame(animIdRef.current);
      }
    };
  }, [loaded, isAnimated, frameCount, frameRate]);

  if (error) {
    return (
      <div className={`flex items-center justify-center bg-slate-900 border border-slate-800 rounded-lg text-slate-500 text-xs ${className}`}>
        Không tải được ảnh
      </div>
    );
  }

  return (
    <div className={`relative flex items-center justify-center overflow-hidden ${className}`}>
      {glow && (
        <div className="absolute inset-0 bg-amber-500/10 rounded-full blur-xl animate-pulse pointer-events-none" />
      )}
      <canvas
        ref={canvasRef}
        className={`pixelated w-full h-full object-contain drop-shadow-md transition-transform duration-200 hover:scale-105 ${
          glow ? 'filter drop-shadow-[0_0_12px_rgba(245,158,11,0.5)]' : ''
        }`}
      />
    </div>
  );
}
