import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import {
  Search,
  Play,
  Pause,
  RotateCcw,
  ZoomIn,
  ZoomOut,
  Maximize2,
  Download,
  Film,
  Archive,
  Layers,
  Eye,
  EyeOff,
  Upload,
  Sparkles,
  Sliders,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Grid,
  Camera,
  FolderOpen,
  Repeat,
  ChevronRight,
  Info
} from 'lucide-react';
import {
  NIKKE_CHARACTERS,
  NIKKE_MANUFACTURERS,
  NIKKE_POSES,
  getNikkeSpineUrlCandidates,
  fetchAndMergeNkasCatalog
} from '../data/nikkeCharacters';
import {
  loadSpineRuntime,
  detectSkelVersion,
  loadImageElement,
  parseAtlasPageNames,
  calculateOptimalGrid
} from '../utils/spineEngine';
import { createZipBlob, canvasToPngBytes } from '../utils/zipBuilder';

const RESOLUTION_PRESETS = [
  { id: '1080p', label: '1080×1080 (Full HD Square)', width: 1080, height: 1080 },
  { id: '1080p_v', label: '1080×1920 (Portrait 9:16)', width: 1080, height: 1920 },
  { id: '720p', label: '720×720 (HD Square)', width: 720, height: 720 },
  { id: '512', label: '512×512 (Totem Ultra HD)', width: 512, height: 512 },
  { id: '256', label: '256×256 (Totem HD)', width: 256, height: 256 },
  { id: '128', label: '128×128 (Totem Standard)', width: 128, height: 128 },
];

export default function NikkeL2DViewer({ onSendToTotemGallery }) {
  // Character & Pose Selection State
  const [catalog, setCatalog] = useState(NIKKE_CHARACTERS);
  const [searchQuery, setSearchQuery] = useState('');
  const [manufacturerFilter, setManufacturerFilter] = useState('all');
  const [selectedChar, setSelectedChar] = useState(NIKKE_CHARACTERS[0]); // Dorothy default
  const [selectedSkin, setSelectedSkin] = useState('00');
  const [selectedPose, setSelectedPose] = useState('fb'); // 'fb' | 'aim' | 'cover'

  // Custom URL / Local File Override
  const [customIdInput, setCustomIdInput] = useState('');
  const [activeTabRight, setActiveTabRight] = useState('export'); // 'export' | 'layers' | 'custom'

  // Spine Runtime & Model State
  const [isLoadingModel, setIsLoadingModel] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [loadedModelInfo, setLoadedModelInfo] = useState(null);

  const [animations, setAnimations] = useState([]);
  const [currentAnim, setCurrentAnim] = useState('idle');
  const [animDuration, setAnimDuration] = useState(0);
  const [animTime, setAnimTime] = useState(0);
  const [isPlaying, setIsPlaying] = useState(true);
  const [isLooping, setIsLooping] = useState(true);
  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
  const [usePma, setUsePma] = useState(true);

  // Slots / Layer Visibility
  const [slotList, setSlotList] = useState([]);
  const [slotSearch, setSlotSearch] = useState('');
  const hiddenSlotsRef = useRef(new Set());

  // Camera & Viewport State
  const [cameraZoom, setCameraZoom] = useState(1.0);
  const [bgMode, setBgMode] = useState('checker'); // 'checker' | 'dark' | 'green' | 'magenta'
  const [showCropGuide, setShowCropGuide] = useState(false);

  // Export Settings
  const [exportResId, setExportResId] = useState('1080p');
  const [exportFps, setExportFps] = useState(60);
  const [totemFrameSize, setTotemFrameSize] = useState(256);
  const [isExporting, setIsExporting] = useState(false);
  const [exportProgress, setExportProgress] = useState({ current: 0, total: 0, label: '' });
  const [exportSuccessMsg, setExportSuccessMsg] = useState(null);

  // WebGL & Spine Refs
  const canvasRef = useRef(null);
  const spineCtxRef = useRef({
    spine: null,
    gl: null,
    shader: null,
    batcher: null,
    mvp: null,
    skeletonRenderer: null,
    skeleton: null,
    state: null,
    bounds: null,
    textures: [],
    baseCamX: 0,
    baseCamY: 0,
    baseCamScale: 1,
    userPanX: 0,
    userPanY: 0,
    userZoom: 1.0,
  });

  const isPlayingRef = useRef(isPlaying);
  isPlayingRef.current = isPlaying;

  const playbackSpeedRef = useRef(playbackSpeed);
  playbackSpeedRef.current = playbackSpeed;

  const usePmaRef = useRef(usePma);
  usePmaRef.current = usePma;

  const isExportingRef = useRef(isExporting);
  isExportingRef.current = isExporting;

  // Dragging canvas state
  const dragStateRef = useRef({ dragging: false, lastX: 0, lastY: 0 });

  const currentResolution = useMemo(() => {
    return RESOLUTION_PRESETS.find((r) => r.id === exportResId) || RESOLUTION_PRESETS[0];
  }, [exportResId]);

  // Dynamically fetch live character/skin index from nkas-l2d.pages.dev/characters.json
  useEffect(() => {
    let mounted = true;
    fetchAndMergeNkasCatalog(NIKKE_CHARACTERS).then((merged) => {
      if (mounted && Array.isArray(merged) && merged.length > 0) {
        setCatalog(merged);
        setSelectedChar((prev) => merged.find((c) => c.id === prev?.id) || merged[0]);
      }
    });
    return () => {
      mounted = false;
    };
  }, []);

  // Filtered character catalog
  const filteredCharacters = useMemo(() => {
    return catalog.filter((c) => {
      if (manufacturerFilter !== 'all' && c.manufacturer !== manufacturerFilter) {
        return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        return (
          c.name.toLowerCase().includes(q) ||
          c.id.toLowerCase().includes(q) ||
          (c.squad || '').toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [catalog, manufacturerFilter, searchQuery]);

  // Cleanup WebGL textures
  const disposeCurrentTextures = () => {
    const ctx = spineCtxRef.current;
    if (ctx.textures && ctx.textures.length > 0) {
      ctx.textures.forEach((t) => {
        try {
          if (t && t.dispose) t.dispose();
        } catch (e) {
          // ignore
        }
      });
      ctx.textures = [];
    }
  };

  // Compute bounding box of skeleton in setup/idle pose
  const calculateBounds = (spine, skeleton) => {
    skeleton.setToSetupPose();
    skeleton.updateWorldTransform();
    const offset = new spine.Vector2();
    const size = new spine.Vector2();
    skeleton.getBounds(offset, size, []);

    // Fallback if bounds are empty or infinity
    if (!isFinite(offset.x) || !isFinite(size.x) || size.x <= 1 || size.y <= 1) {
      return {
        x: -500,
        y: 0,
        width: 1000,
        height: 1800,
      };
    }
    return {
      x: offset.x,
      y: offset.y,
      width: size.x,
      height: size.y,
    };
  };

  // Render a single frame to the WebGL canvas (with alpha = 0 transparent background!)
  const renderCurrentFrame = useCallback(() => {
    const canvas = canvasRef.current;
    const ctx = spineCtxRef.current;
    if (!canvas || !ctx.gl || !ctx.skeleton || !ctx.state || !ctx.spine) return;

    const { gl, spine, shader, batcher, mvp, skeletonRenderer, skeleton } = ctx;

    // Ensure Hidden Slots have attachment hidden or alpha = 0 before drawing
    const hiddenSet = hiddenSlotsRef.current;
    if (skeleton.slots) {
      for (let i = 0; i < skeleton.slots.length; i++) {
        const slot = skeleton.slots[i];
        const slotName = slot.data.name;
        if (hiddenSet.has(slotName)) {
          slot.color.a = 0;
        }
      }
    }

    gl.viewport(0, 0, canvas.width, canvas.height);
    // Crucial: Always clear with (0, 0, 0, 0) so exported PNGs & WebM videos have 100% transparent background!
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    // Calculate orthographic camera matrix
    const centerX = ctx.baseCamX - ctx.userPanX;
    const centerY = ctx.baseCamY + ctx.userPanY;
    const zoom = Math.max(0.05, ctx.userZoom);
    const viewW = (canvas.width * ctx.baseCamScale) / zoom;
    const viewH = (canvas.height * ctx.baseCamScale) / zoom;

    mvp.ortho2d(centerX - viewW / 2, centerY - viewH / 2, viewW, viewH);

    shader.bind();
    shader.setUniformi(spine.Shader.SAMPLER, 0);
    shader.setUniform4x4f(spine.Shader.MVP_MATRIX, mvp.values);

    batcher.begin(shader);
    skeletonRenderer.premultipliedAlpha = usePmaRef.current;
    skeletonRenderer.draw(batcher, skeleton);
    batcher.end();
    shader.unbind();
  }, []);

  // Main Animation Loop (requestAnimationFrame)
  useEffect(() => {
    let rafId;
    let lastTime = performance.now();

    const tick = (now) => {
      rafId = requestAnimationFrame(tick);
      if (isExportingRef.current) {
        lastTime = now;
        return;
      }

      const dt = Math.min((now - lastTime) / 1000, 0.1);
      lastTime = now;

      const ctx = spineCtxRef.current;
      if (!ctx.skeleton || !ctx.state) return;

      if (isPlayingRef.current) {
        ctx.state.update(dt * playbackSpeedRef.current);
        ctx.state.apply(ctx.skeleton);
        ctx.skeleton.updateWorldTransform();

        const track = ctx.state.getCurrent(0);
        if (track && track.animation) {
          const d = track.animation.duration || 1;
          setAnimTime(track.trackTime % (d > 0 ? d : 1));
        }
      }

      renderCurrentFrame();
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [renderCurrentFrame]);

  // Initialize Spine Skeleton from loaded Raw Buffers/Images
  const buildSpineScene = async ({
    skelBuffer,
    jsonText,
    atlasText,
    pageImagesMap,
    modelTitle,
  }) => {
    const canvas = canvasRef.current;
    if (!canvas) throw new Error('Canvas chưa sẵn sàng');

    let gl = spineCtxRef.current.gl;
    if (!gl) {
      const config = {
        alpha: true,
        premultipliedAlpha: true,
        preserveDrawingBuffer: true,
        antialias: true,
      };
      gl = canvas.getContext('webgl2', config) || canvas.getContext('webgl', config);
      if (!gl) throw new Error('Trình duyệt không hỗ trợ WebGL');
      spineCtxRef.current.gl = gl;
    }

    disposeCurrentTextures();

    // Auto-detect Spine 4.0 vs 4.1 from binary header, with automatic fallback
    const preferredVer = skelBuffer ? detectSkelVersion(skelBuffer) : '4.1';
    const fallbackVer = preferredVer === '4.0' ? '4.1' : '4.0';
    let spine = await loadSpineRuntime(preferredVer);
    let skeletonData = null;
    let createdTextures = [];

    const createSkeletonWithRuntime = (rt) => {
      createdTextures = [];
      const atlas = new rt.TextureAtlas(atlasText, (path) => {
        const cleanName = path.split('/').pop();
        const img = pageImagesMap[path] || pageImagesMap[cleanName] || Object.values(pageImagesMap)[0];
        if (!img) {
          throw new Error(`Thiếu file texture: ${path}`);
        }
        const glTex = new rt.GLTexture(gl, img, false);
        glTex.setFilters(rt.TextureFilter.Linear, rt.TextureFilter.Linear);
        glTex.setWraps(rt.TextureWrap.ClampToEdge, rt.TextureWrap.ClampToEdge);
        createdTextures.push(glTex);
        return glTex;
      });

      const atlasLoader = new rt.AtlasAttachmentLoader(atlas);
      if (jsonText) {
        const skelJson = new rt.SkeletonJson(atlasLoader);
        return skelJson.readSkeletonData(jsonText);
      } else {
        const skelBin = new rt.SkeletonBinary(atlasLoader);
        const uint8 = skelBuffer instanceof Uint8Array ? skelBuffer : new Uint8Array(skelBuffer);
        return skelBin.readSkeletonData(uint8);
      }
    };

    try {
      skeletonData = createSkeletonWithRuntime(spine);
    } catch (errPrimary) {
      console.warn(`Thử tải với Spine ${preferredVer} thất bại, đang chuyển sang Spine ${fallbackVer}...`, errPrimary);
      createdTextures.forEach((t) => t?.dispose?.());
      spine = await loadSpineRuntime(fallbackVer);
      skeletonData = createSkeletonWithRuntime(spine);
    }

    // Create WebGL Shader, Batcher, Renderer for this Spine runtime
    const shader = spine.Shader.newTwoColoredTextured(gl);
    const batcher = new spine.PolygonBatcher(gl, true, 10920);
    const mvp = new spine.Matrix4();
    const skeletonRenderer = new spine.SkeletonRenderer(gl);
    skeletonRenderer.premultipliedAlpha = true;

    const skeleton = new spine.Skeleton(skeletonData);
    // Set default skin if needed
    if (skeletonData.skins && skeletonData.skins.length > 0) {
      const defaultSkin =
        skeletonData.findSkin('default') ||
        skeletonData.skins[skeletonData.skins.length - 1];
      if (defaultSkin) {
        skeleton.setSkin(defaultSkin);
        skeleton.setSlotsToSetupPose();
      }
    }

    const stateData = new spine.AnimationStateData(skeletonData);
    const state = new spine.AnimationState(stateData);

    // Extract animations list
    const animList = (skeletonData.animations || []).map((a) => ({
      name: a.name,
      duration: a.duration,
    }));

    // Pick 'idle' or first animation
    const defaultAnimObj =
      animList.find((a) => a.name === 'idle') ||
      animList.find((a) => a.name.toLowerCase().includes('idle')) ||
      animList[0];

    const initialAnimName = defaultAnimObj ? defaultAnimObj.name : '';
    const initialDuration = defaultAnimObj ? defaultAnimObj.duration : 0;

    if (initialAnimName) {
      state.setAnimation(0, initialAnimName, true);
      state.apply(skeleton);
      skeleton.updateWorldTransform();
    }

    // Compute camera bounds based on current animation frame 0
    const offset = new spine.Vector2();
    const size = new spine.Vector2();
    skeleton.getBounds(offset, size, []);

    let bounds = {
      x: offset.x,
      y: offset.y,
      width: size.x,
      height: size.y,
    };
    if (!isFinite(bounds.x) || !isFinite(bounds.width) || bounds.width <= 5 || bounds.height <= 5) {
      bounds = calculateBounds(spine, skeleton);
      if (initialAnimName) {
        state.setAnimation(0, initialAnimName, true);
        state.apply(skeleton);
        skeleton.updateWorldTransform();
      }
    }

    const padding = 1.18;
    const scaleX = (bounds.width * padding) / canvas.width;
    const scaleY = (bounds.height * padding) / canvas.height;
    const baseScale = Math.max(scaleX, scaleY, 0.1);

    // Extract Slots for Layer Inspection (like BD2-L2D-Viewer)
    const slots = (skeleton.slots || []).map((s, idx) => ({
      index: idx,
      name: s.data.name,
      attachmentName: s.getAttachment() ? s.getAttachment().name : null,
    }));

    hiddenSlotsRef.current = new Set();

    spineCtxRef.current = {
      ...spineCtxRef.current,
      spine,
      gl,
      shader,
      batcher,
      mvp,
      skeletonRenderer,
      skeleton,
      state,
      bounds,
      textures: createdTextures,
      baseCamX: bounds.x + bounds.width / 2,
      baseCamY: bounds.y + bounds.height / 2,
      baseCamScale: baseScale,
      userPanX: 0,
      userPanY: 0,
      userZoom: 1.0,
    };

    setCameraZoom(1.0);
    setAnimations(animList);
    setCurrentAnim(initialAnimName);
    setAnimDuration(initialDuration);
    setAnimTime(0);
    setSlotList(slots);
    setLoadedModelInfo({
      title: modelTitle,
      animCount: animList.length,
      slotCount: slots.length,
      pageCount: createdTextures.length,
    });
    setIsPlaying(true);
    renderCurrentFrame();
  };

  // Load Remote Nikke Character Model from NKAS / Nikke-DB CDN with multi-path fallback
  const loadRemoteNikkeModel = useCallback(async (charObj, skinCode, poseId) => {
    setIsLoadingModel(true);
    setLoadError(null);
    setExportSuccessMsg(null);

    try {
      const candidates = getNikkeSpineUrlCandidates(charObj.id, skinCode, poseId);
      let resolvedUrls = null;
      let atlasText = null;

      // 1. Try candidate URLs in order until .atlas is found (200 OK)
      for (const cand of candidates) {
        try {
          const res = await fetch(cand.atlasUrl);
          if (res.ok) {
            const text = await res.text();
            if (text && text.includes('.png')) {
              resolvedUrls = cand;
              atlasText = text;
              break;
            }
          }
        } catch (e) {
          // continue to next candidate
        }
      }

      if (!resolvedUrls || !atlasText) {
        throw new Error(
          `Không tìm thấy dữ liệu model (${charObj.id}_${skinCode} - ${poseId.toUpperCase()}) trên kho nkas-l2d.pages.dev. Hãy thử chọn Skin [00] Default hoặc tư thế khác.`
        );
      }

      // 2. Parse all texture PNG pages required by the .atlas
      const pageNames = parseAtlasPageNames(atlasText);
      if (pageNames.length === 0) {
        pageNames.push(`${resolvedUrls.fileStem}.png`);
      }

      // 3. Fetch .skel binary and all texture PNGs in parallel
      const [skelRes, ...loadedImages] = await Promise.all([
        fetch(resolvedUrls.skelUrl),
        ...pageNames.map((pageName) => loadImageElement(`${resolvedUrls.basePath}${pageName}`)),
      ]);

      if (!skelRes.ok) {
        throw new Error(`Không thể tải file .skel (${skelRes.status})`);
      }
      const skelBuffer = await skelRes.arrayBuffer();

      const pageImagesMap = {};
      pageNames.forEach((name, idx) => {
        pageImagesMap[name] = loadedImages[idx];
      });

      const poseLabel = NIKKE_POSES.find((p) => p.id === poseId)?.label || poseId;
      await buildSpineScene({
        skelBuffer,
        atlasText,
        pageImagesMap,
        modelTitle: `${charObj.name} (${resolvedUrls.fileStem}) • ${poseLabel}`,
      });
    } catch (err) {
      console.error(err);
      setLoadError(err.message || 'Lỗi khi tải model từ CDN');
    } finally {
      setIsLoadingModel(false);
    }
  }, []);

  // Load initial character on mount or when character/skin/pose changes
  useEffect(() => {
    if (selectedChar) {
      loadRemoteNikkeModel(selectedChar, selectedSkin, selectedPose);
    }
  }, [selectedChar, selectedSkin, selectedPose, loadRemoteNikkeModel]);

  // Handle Custom Local Files Upload (.skel/.json + .atlas + .png)
  const handleLocalFilesUpload = async (e) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    setIsLoadingModel(true);
    setLoadError(null);

    try {
      const atlasFile = files.find((f) => f.name.toLowerCase().endsWith('.atlas') || f.name.toLowerCase().endsWith('.atlas.txt'));
      const skelFile = files.find((f) => f.name.toLowerCase().endsWith('.skel'));
      const jsonFile = files.find((f) => f.name.toLowerCase().endsWith('.json'));
      const pngFiles = files.filter((f) => /\.(png|webp|jpg|jpeg)$/i.test(f.name));

      if (!atlasFile) {
        throw new Error('Vui lòng chọn kèm file .atlas!');
      }
      if (!skelFile && !jsonFile) {
        throw new Error('Vui lòng chọn kèm file .skel hoặc .json!');
      }
      if (pngFiles.length === 0) {
        throw new Error('Vui lòng chọn kèm ít nhất 1 file ảnh .png!');
      }

      const atlasText = await atlasFile.text();
      const skelBuffer = skelFile ? await skelFile.arrayBuffer() : null;
      const jsonText = jsonFile ? await jsonFile.text() : null;

      const pageImagesMap = {};
      for (const imgFile of pngFiles) {
        const objUrl = URL.createObjectURL(imgFile);
        const imgEl = await loadImageElement(objUrl);
        pageImagesMap[imgFile.name] = imgEl;
      }

      const title = (skelFile || jsonFile).name.replace(/\.(skel|json)$/i, '');
      await buildSpineScene({
        skelBuffer,
        jsonText,
        atlasText,
        pageImagesMap,
        modelTitle: `Local: ${title}`,
      });
    } catch (err) {
      setLoadError(err.message || 'Lỗi khi nạp file local');
    } finally {
      setIsLoadingModel(false);
    }
  };

  // Switch animation
  const handleSelectAnimation = (animName) => {
    const ctx = spineCtxRef.current;
    if (!ctx.state || !ctx.skeleton) return;

    setCurrentAnim(animName);
    ctx.state.setAnimation(0, animName, isLooping);
    ctx.state.apply(ctx.skeleton);
    ctx.skeleton.updateWorldTransform();

    const found = animations.find((a) => a.name === animName);
    setAnimDuration(found ? found.duration : 0);
    setAnimTime(0);
    renderCurrentFrame();
  };

  // Toggle Looping
  const handleToggleLoop = () => {
    const nextLoop = !isLooping;
    setIsLooping(nextLoop);
    const ctx = spineCtxRef.current;
    if (ctx.state && currentAnim) {
      const track = ctx.state.getCurrent(0);
      if (track) {
        track.loop = nextLoop;
      }
    }
  };

  // Scrub Timeline
  const handleTimelineScrub = (newTime) => {
    const ctx = spineCtxRef.current;
    if (!ctx.state || !ctx.skeleton) return;

    setIsPlaying(false);
    setAnimTime(newTime);
    const track = ctx.state.getCurrent(0);
    if (track) {
      track.trackTime = newTime;
      ctx.state.apply(ctx.skeleton);
      ctx.skeleton.updateWorldTransform();
      renderCurrentFrame();
    }
  };

  // Toggle Layer / Slot visibility
  const handleToggleSlot = (slotName) => {
    const hidden = hiddenSlotsRef.current;
    const ctx = spineCtxRef.current;
    if (hidden.has(slotName)) {
      hidden.delete(slotName);
      if (ctx.skeleton) {
        const slot = ctx.skeleton.findSlot(slotName);
        if (slot) slot.color.a = 1;
      }
    } else {
      hidden.add(slotName);
      if (ctx.skeleton) {
        const slot = ctx.skeleton.findSlot(slotName);
        if (slot) slot.color.a = 0;
      }
    }
    setSlotList((prev) => [...prev]);
    renderCurrentFrame();
  };

  // Camera Pan & Zoom Handlers
  const handleCanvasMouseDown = (e) => {
    if (e.button !== 0) return;
    dragStateRef.current = {
      dragging: true,
      lastX: e.clientX,
      lastY: e.clientY,
    };
  };

  const handleCanvasMouseMove = (e) => {
    if (!dragStateRef.current.dragging) return;
    const dx = e.clientX - dragStateRef.current.lastX;
    const dy = e.clientY - dragStateRef.current.lastY;
    dragStateRef.current.lastX = e.clientX;
    dragStateRef.current.lastY = e.clientY;

    const ctx = spineCtxRef.current;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const ratioX = canvas.width / Math.max(1, rect.width);
    const ratioY = canvas.height / Math.max(1, rect.height);
    const worldFactor = ctx.baseCamScale / Math.max(0.05, ctx.userZoom);

    ctx.userPanX += dx * ratioX * worldFactor;
    ctx.userPanY += dy * ratioY * worldFactor;
    renderCurrentFrame();
  };

  const handleCanvasMouseUp = () => {
    dragStateRef.current.dragging = false;
  };

  const handleCanvasWheel = (e) => {
    e.preventDefault();
    const ctx = spineCtxRef.current;
    const factor = e.deltaY < 0 ? 1.12 : 0.89;
    const nextZoom = Math.min(8.0, Math.max(0.2, ctx.userZoom * factor));
    ctx.userZoom = nextZoom;
    setCameraZoom(Number(nextZoom.toFixed(2)));
    renderCurrentFrame();
  };

  const handleResetCamera = () => {
    const ctx = spineCtxRef.current;
    ctx.userPanX = 0;
    ctx.userPanY = 0;
    ctx.userZoom = 1.0;
    setCameraZoom(1.0);
    renderCurrentFrame();
  };

  // Focus Face / Upper Body Preset (Ideal for Minecraft Totem!)
  const handleFocusUpperBody = () => {
    const ctx = spineCtxRef.current;
    if (!ctx.bounds) return;
    ctx.userZoom = 2.2;
    ctx.userPanX = 0;
    ctx.userPanY = ctx.bounds.height * 0.24;
    setCameraZoom(2.2);
    renderCurrentFrame();
  };

  // Handle Resolution Change -> Update WebGL Canvas dimensions & camera scale
  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = spineCtxRef.current;
    if (!canvas) return;
    canvas.width = currentResolution.width;
    canvas.height = currentResolution.height;

    if (ctx.bounds) {
      const padding = 1.18;
      const scaleX = (ctx.bounds.width * padding) / canvas.width;
      const scaleY = (ctx.bounds.height * padding) / canvas.height;
      ctx.baseCamScale = Math.max(scaleX, scaleY, 0.1);
      renderCurrentFrame();
    }
  }, [currentResolution, renderCurrentFrame]);

  // ============================================================================
  // EXPORT 1: 60FPS PNG FRAME SEQUENCE (.ZIP) - Deterministic Step-by-Step!
  // ============================================================================
  const handleExportZipFrames = async () => {
    const ctx = spineCtxRef.current;
    const canvas = canvasRef.current;
    if (!ctx.skeleton || !ctx.state || !canvas || isExporting) return;

    const fps = exportFps || 60;
    const duration = Math.max(0.2, animDuration || 2.0);
    const totalFrames = Math.max(1, Math.round(duration * fps));
    const dt = 1 / fps;

    setIsExporting(true);
    setExportSuccessMsg(null);
    setExportProgress({ current: 0, total: totalFrames, label: `Đang kết xuất ${totalFrames} frame PNG (${fps} FPS)...` });

    const prevPlaying = isPlaying;
    setIsPlaying(false);

    try {
      // Reset animation to exact t = 0
      ctx.skeleton.setToSetupPose();
      ctx.state.setAnimation(0, currentAnim, false);

      const files = [];
      for (let i = 0; i < totalFrames; i++) {
        if (i === 0) {
          ctx.state.update(0);
        } else {
          ctx.state.update(dt);
        }
        ctx.state.apply(ctx.skeleton);
        ctx.skeleton.updateWorldTransform();
        renderCurrentFrame();

        const pngBytes = await canvasToPngBytes(canvas);
        const frameName = `frame_${String(i).padStart(4, '0')}.png`;
        files.push({ name: frameName, data: pngBytes });

        if (i % 3 === 0 || i === totalFrames - 1) {
          setExportProgress({
            current: i + 1,
            total: totalFrames,
            label: `Đang trích xuất frame ${i + 1}/${totalFrames} (${fps} FPS - Nền trong suốt)...`,
          });
          await new Promise((r) => setTimeout(r, 0));
        }
      }

      setExportProgress({
        current: totalFrames,
        total: totalFrames,
        label: 'Đang đóng gói file .ZIP...',
      });
      await new Promise((r) => setTimeout(r, 10));

      const zipBlob = createZipBlob(files);
      const safeName = `${selectedChar?.name || 'nikke'}_${selectedChar?.id || 'l2d'}_${selectedPose}_${currentAnim}_${fps}fps_${totalFrames}f`
        .toLowerCase()
        .replace(/[^a-z0-9_-]/g, '_');

      const url = URL.createObjectURL(zipBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${safeName}_frames.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);

      setExportSuccessMsg(
        `Đã xuất thành công ${totalFrames} frame PNG (${canvas.width}×${canvas.height}px @ ${fps} FPS, nền trong suốt) vào file ${safeName}_frames.zip!`
      );
    } catch (err) {
      console.error(err);
      setLoadError(`Lỗi khi xuất ZIP: ${err.message}`);
    } finally {
      // Restore animation loop
      ctx.state.setAnimation(0, currentAnim, isLooping);
      setIsPlaying(prevPlaying);
      setIsExporting(false);
    }
  };

  // ============================================================================
  // EXPORT 2: TRANSPARENT WEBM VIDEO (VP9 / VP8 ALPHA 60FPS)
  // ============================================================================
  const handleExportTransparentVideo = async () => {
    const ctx = spineCtxRef.current;
    const canvas = canvasRef.current;
    if (!ctx.skeleton || !ctx.state || !canvas || isExporting) return;

    const fps = exportFps || 60;
    const duration = Math.max(0.5, animDuration || 2.5);
    const totalFrames = Math.max(1, Math.round(duration * fps));
    const dt = 1 / fps;

    // Pick best WebM codec supporting Alpha transparency
    const mimeCandidates = [
      'video/webm;codecs=vp9',
      'video/webm;codecs=vp8',
      'video/webm',
    ];
    const supportedMime = mimeCandidates.find((m) => MediaRecorder.isTypeSupported(m)) || 'video/webm';

    setIsExporting(true);
    setExportSuccessMsg(null);
    setExportProgress({
      current: 0,
      total: totalFrames,
      label: `Đang ghi Video WebM không nền (${fps} FPS)...`,
    });

    const prevPlaying = isPlaying;
    setIsPlaying(false);

    try {
      // Capture stream at 0 FPS so we manually requestFrame() on each deterministic frame!
      const stream = canvas.captureStream(0);
      const videoTrack = stream.getVideoTracks()[0];

      const chunks = [];
      const recorder = new MediaRecorder(stream, {
        mimeType: supportedMime,
        videoBitsPerSecond: 16_000_000, // 16 Mbps high quality
      });

      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) chunks.push(e.data);
      };

      const stopPromise = new Promise((resolve) => {
        recorder.onstop = () => resolve();
      });

      ctx.skeleton.setToSetupPose();
      ctx.state.setAnimation(0, currentAnim, false);

      recorder.start();

      for (let i = 0; i < totalFrames; i++) {
        if (i === 0) {
          ctx.state.update(0);
        } else {
          ctx.state.update(dt);
        }
        ctx.state.apply(ctx.skeleton);
        ctx.skeleton.updateWorldTransform();
        renderCurrentFrame();

        // Trigger frame capture on stream track
        if (videoTrack && typeof videoTrack.requestFrame === 'function') {
          videoTrack.requestFrame();
        }

        setExportProgress({
          current: i + 1,
          total: totalFrames,
          label: `Đang mã hóa Video không nền: Frame ${i + 1}/${totalFrames} (${fps} FPS)...`,
        });

        // Wait ~16.67ms (1000/fps) so MediaRecorder timestamps match exact 60fps playback speed
        await new Promise((r) => setTimeout(r, 1000 / fps));
      }

      recorder.stop();
      await stopPromise;

      const webmBlob = new Blob(chunks, { type: supportedMime });
      const safeName = `${selectedChar?.name || 'nikke'}_${selectedChar?.id || 'l2d'}_${selectedPose}_${currentAnim}_${fps}fps_alpha`
        .toLowerCase()
        .replace(/[^a-z0-9_-]/g, '_');

      const url = URL.createObjectURL(webmBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${safeName}.webm`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);

      setExportSuccessMsg(
        `Đã xuất thành công Video không nền ${safeName}.webm (${canvas.width}×${canvas.height}px @ ${fps} FPS)!`
      );
    } catch (err) {
      console.error(err);
      setLoadError(`Lỗi khi xuất Video WebM: ${err.message}`);
    } finally {
      ctx.state.setAnimation(0, currentAnim, isLooping);
      setIsPlaying(prevPlaying);
      setIsExporting(false);
    }
  };

  // ============================================================================
  // EXPORT 3: DIRECT MINECRAFT TOTEM SPRITESHEET PNG (2D GRID / VERTICAL)
  // ============================================================================
  const handleExportMinecraftTotem = async (sendToGallery = false) => {
    const ctx = spineCtxRef.current;
    const canvas = canvasRef.current;
    if (!ctx.skeleton || !ctx.state || !canvas || isExporting) return;

    const fps = exportFps || 60;
    const duration = Math.min(4.0, Math.max(0.5, animDuration || 2.0));
    const totalFrames = Math.max(1, Math.round(duration * fps));
    const dt = 1 / fps;
    const size = totemFrameSize || 256;

    const { cols, rows } = calculateOptimalGrid(totalFrames, size);
    const sheetCanvas = document.createElement('canvas');
    sheetCanvas.width = cols * size;
    sheetCanvas.height = rows * size;
    const sheetCtx = sheetCanvas.getContext('2d');

    setIsExporting(true);
    setExportSuccessMsg(null);
    setExportProgress({
      current: 0,
      total: totalFrames,
      label: `Đang tạo Spritesheet Totem ${cols}×${rows} (${totalFrames} frames @ ${fps} FPS)...`,
    });

    const prevPlaying = isPlaying;
    setIsPlaying(false);

    try {
      ctx.skeleton.setToSetupPose();
      ctx.state.setAnimation(0, currentAnim, false);

      // Center crop square region from WebGL canvas
      const srcMin = Math.min(canvas.width, canvas.height);
      const sx = (canvas.width - srcMin) / 2;
      const sy = (canvas.height - srcMin) / 2;

      for (let i = 0; i < totalFrames; i++) {
        if (i === 0) {
          ctx.state.update(0);
        } else {
          ctx.state.update(dt);
        }
        ctx.state.apply(ctx.skeleton);
        ctx.skeleton.updateWorldTransform();
        renderCurrentFrame();

        const col = i % cols;
        const row = Math.floor(i / cols);
        sheetCtx.drawImage(canvas, sx, sy, srcMin, srcMin, col * size, row * size, size, size);

        if (i % 5 === 0 || i === totalFrames - 1) {
          setExportProgress({
            current: i + 1,
            total: totalFrames,
            label: `Đang ghép frame Totem ${i + 1}/${totalFrames}...`,
          });
          await new Promise((r) => setTimeout(r, 0));
        }
      }

      const dataUrl = sheetCanvas.toDataURL('image/png');
      const gridSuffix = cols > 1 ? `_grid${cols}x${rows}_${totalFrames}f` : '';
      const safeName = `${selectedChar?.name || 'nikke'}_${selectedPose}_${currentAnim}${gridSuffix}_${fps}fps`
        .toLowerCase()
        .replace(/[^a-z0-9_-]/g, '_');

      if (sendToGallery && onSendToTotemGallery) {
        onSendToTotemGallery({
          id: `nikke_${Date.now()}`,
          title: `${selectedChar?.name || 'Nikke'} (${currentAnim.toUpperCase()})`,
          author: 'Nikke L2D Studio',
          category: 'anime',
          description: `Trích xuất trực tiếp từ Nikke Live2D Studio (${totalFrames} frames @ ${fps} FPS, độ phân giải ${size}×${size}px).`,
          imageUrl: dataUrl,
          isAnimated: true,
          frames: totalFrames,
          columns: cols,
          rows: rows,
          fps: fps,
          glow: true,
          downloads: 1,
          likes: 1,
          tags: ['nikke', 'live2d', (selectedChar?.name || 'spine').toLowerCase(), `${fps}fps`],
          createdAt: new Date().toISOString().split('T')[0],
        });
        setExportSuccessMsg(`Đã lưu trực tiếp Totem "${selectedChar?.name}" vào Thư viện TotemHub!`);
      } else {
        const a = document.createElement('a');
        a.href = dataUrl;
        a.download = `${safeName}.png`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setExportSuccessMsg(
          `Đã tải xuống Totem Spritesheet ${safeName}.png (${sheetCanvas.width}×${sheetCanvas.height}px)! Bạn có thể chép thẳng vào .minecraft/config/custom_totems/images/`
        );
      }
    } catch (err) {
      console.error(err);
      setLoadError(`Lỗi khi tạo Totem Spritesheet: ${err.message}`);
    } finally {
      ctx.state.setAnimation(0, currentAnim, isLooping);
      setIsPlaying(prevPlaying);
      setIsExporting(false);
    }
  };

  // Snapshot Single PNG Frame
  const handleSnapshotPng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    renderCurrentFrame();
    const url = canvas.toDataURL('image/png');
    const a = document.createElement('a');
    a.href = url;
    a.download = `${selectedChar?.name || 'nikke'}_${currentAnim}_snapshot.png`;
    document.body.appendChild(a);
    a.click();
    a.remove();
  };

  // Load any custom character ID typed by user (e.g. c010, c510)
  const handleLoadCustomCharId = (e) => {
    e.preventDefault();
    const clean = customIdInput.trim().toLowerCase();
    if (!clean) return;
    const customChar = {
      id: clean,
      name: `Custom (${clean.toUpperCase()})`,
      manufacturer: 'Custom',
      squad: 'Custom ID',
      accent: '#f59e0b',
      skins: [
        { code: '00', name: 'Skin 00 (Default)' },
        { code: '01', name: 'Skin 01' },
        { code: '02', name: 'Skin 02' },
      ],
    };
    setSelectedChar(customChar);
    setSelectedSkin('00');
  };

  // Background CSS style for Viewport preview (Canvas itself remains transparent!)
  const getViewportBgStyle = () => {
    if (bgMode === 'checker') {
      return {
        backgroundImage:
          'linear-gradient(45deg, #1e293b 25%, transparent 25%), linear-gradient(-45deg, #1e293b 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #1e293b 75%), linear-gradient(-45deg, transparent 75%, #1e293b 75%)',
        backgroundSize: '24px 24px',
        backgroundPosition: '0 0, 0 12px, 12px -12px, -12px 0px',
        backgroundColor: '#0f172a',
      };
    }
    if (bgMode === 'green') return { backgroundColor: '#00b140' };
    if (bgMode === 'magenta') return { backgroundColor: '#ff00ff' };
    return { backgroundColor: '#090d16' };
  };

  const filteredSlots = useMemo(() => {
    if (!slotSearch.trim()) return slotList;
    const q = slotSearch.toLowerCase();
    return slotList.filter((s) => s.name.toLowerCase().includes(q));
  }, [slotList, slotSearch]);

  return (
    <div className="flex-1 flex flex-col lg:flex-row h-[calc(100vh-4rem)] overflow-hidden bg-slate-950 text-slate-100">
      {/* =====================================================================
          LEFT SIDEBAR: CHARACTER, COSTUME & POSE SELECTOR (BD2-L2D-Viewer Style)
         ===================================================================== */}
      <aside className="w-full lg:w-80 border-b lg:border-b-0 lg:border-r border-slate-800/80 bg-slate-900/50 flex flex-col shrink-0 max-h-[38vh] lg:max-h-none">
        {/* Search & Manufacturer Filter */}
        <div className="p-3.5 border-b border-slate-800/80 space-y-2.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-amber-400 flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5" strokeWidth={1.75} />
              <span>Danh Sách Nikke ({filteredCharacters.length})</span>
            </span>
            <span className="text-[11px] text-slate-400 font-mono">{selectedChar?.id}_{selectedSkin}</span>
          </div>

          <div className="relative">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" strokeWidth={1.75} />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm tên Nikke, mã ID (vd: Dorothy, c220)..."
              className="w-full bg-slate-950/90 border border-slate-800 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
            />
          </div>

          {/* Manufacturer Pills */}
          <div className="flex items-center gap-1 overflow-x-auto pb-1 no-scrollbar">
            {NIKKE_MANUFACTURERS.map((m) => (
              <button
                key={m.id}
                onClick={() => setManufacturerFilter(m.id)}
                className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold whitespace-nowrap transition-all cursor-pointer ${
                  manufacturerFilter === m.id
                    ? 'bg-amber-400 text-slate-950 shadow-sm'
                    : 'bg-slate-950/70 text-slate-400 hover:text-slate-200 border border-slate-800'
                }`}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>

        {/* Character Scroll List */}
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {filteredCharacters.map((char) => {
            const isSelected = selectedChar?.id === char.id;
            return (
              <button
                key={char.id}
                onClick={() => {
                  setSelectedChar(char);
                  setSelectedSkin(char.skins?.[0]?.code || '00');
                }}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-left transition-all cursor-pointer border ${
                  isSelected
                    ? 'bg-amber-500/15 border-amber-500/40 text-white shadow-sm'
                    : 'bg-slate-950/40 hover:bg-slate-900 border-transparent text-slate-300'
                }`}
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div
                    className="w-2.5 h-8 rounded-full shrink-0"
                    style={{ backgroundColor: char.accent || '#f59e0b' }}
                  />
                  <div className="truncate">
                    <div className="text-xs font-bold truncate flex items-center gap-1.5">
                      <span>{char.name}</span>
                    </div>
                    <div className="text-[10px] text-slate-400 flex items-center gap-1.5">
                      <span>{char.manufacturer}</span>
                      <span>•</span>
                      <span className="font-mono text-slate-500">{char.id}</span>
                      <span>•</span>
                      <span>{char.skins?.length || 1} skin</span>
                    </div>
                  </div>
                </div>
                <ChevronRight
                  className={`w-4 h-4 shrink-0 transition-transform ${
                    isSelected ? 'text-amber-400 translate-x-0.5' : 'text-slate-600'
                  }`}
                  strokeWidth={1.75}
                />
              </button>
            );
          })}
        </div>

        {/* Skin & Pose Selector Footer */}
        <div className="p-3 border-t border-slate-800/80 bg-slate-950/80 space-y-2.5">
          {/* Costume / Skin Selector */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-400 mb-1">
              Trang phục (Costume / Skin):
            </label>
            <select
              value={selectedSkin}
              onChange={(e) => setSelectedSkin(e.target.value)}
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-amber-500/50"
            >
              {(selectedChar?.skins || [{ code: '00', name: 'Default' }]).map((sk) => (
                <option key={sk.code} value={sk.code}>
                  [{sk.code}] {sk.name}
                </option>
              ))}
            </select>
          </div>

          {/* Pose Tabs: Standing / Aim / Cover */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-400 mb-1">
              Tư thế (Pose Mode):
            </label>
            <div className="grid grid-cols-3 gap-1 bg-slate-900 p-1 rounded-xl border border-slate-800">
              {NIKKE_POSES.map((pose) => (
                <button
                  key={pose.id}
                  onClick={() => setSelectedPose(pose.id)}
                  className={`py-1.5 px-2 rounded-lg text-[11px] font-semibold transition-all cursor-pointer ${
                    selectedPose === pose.id
                      ? 'bg-amber-400 text-slate-950 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                  title={pose.desc}
                >
                  {pose.id === 'fb' ? 'Standing' : pose.id === 'aim' ? 'Aim' : 'Cover'}
                </button>
              ))}
            </div>
          </div>
        </div>
      </aside>

      {/* =====================================================================
          CENTER STAGE: INTERACTIVE WEBGL CANVAS & TIMELINE CONTROLS
         ===================================================================== */}
      <main className="flex-1 flex flex-col relative min-w-0 bg-slate-950 overflow-hidden">
        {/* Top Floating Viewport Toolbar */}
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 border-b border-slate-800/80 bg-slate-900/60 backdrop-blur-sm z-10">
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-xs font-bold text-slate-200 truncate">
              {loadedModelInfo ? loadedModelInfo.title : 'Đang khởi tạo Spine WebGL...'}
            </span>
            {loadedModelInfo && (
              <span className="hidden sm:inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                Spine WebGL • {currentResolution.width}×{currentResolution.height}
              </span>
            )}
          </div>

          {/* Camera & Background Controls */}
          <div className="flex items-center gap-1.5 flex-wrap">
            {/* Background Checker/Solid Selector */}
            <div className="flex items-center bg-slate-950 border border-slate-800 rounded-xl p-0.5">
              <button
                onClick={() => setBgMode('checker')}
                className={`px-2 py-1 rounded-lg text-[11px] font-medium cursor-pointer ${
                  bgMode === 'checker' ? 'bg-slate-800 text-amber-400' : 'text-slate-400 hover:text-slate-200'
                }`}
                title="Lưới Caro trong suốt (Alpha Checkerboard)"
              >
                Caro (Alpha)
              </button>
              <button
                onClick={() => setBgMode('dark')}
                className={`px-2 py-1 rounded-lg text-[11px] font-medium cursor-pointer ${
                  bgMode === 'dark' ? 'bg-slate-800 text-amber-400' : 'text-slate-400 hover:text-slate-200'
                }`}
                title="Nền tối xem trước"
              >
                Tối
              </button>
              <button
                onClick={() => setBgMode('green')}
                className={`px-2 py-1 rounded-lg text-[11px] font-medium cursor-pointer ${
                  bgMode === 'green' ? 'bg-slate-800 text-emerald-400' : 'text-slate-400 hover:text-slate-200'
                }`}
                title="Phông xanh (Green Screen)"
              >
                Green
              </button>
            </div>

            <button
              onClick={() => setShowCropGuide((v) => !v)}
              className={`p-1.5 rounded-xl border text-xs transition-colors cursor-pointer ${
                showCropGuide
                  ? 'bg-amber-500/20 border-amber-500/40 text-amber-300'
                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
              title="Hiện khung căn chỉnh vuông 1:1"
            >
              <Grid className="w-4 h-4" strokeWidth={1.75} />
            </button>

            <button
              onClick={handleFocusUpperBody}
              className="px-2.5 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-[11px] font-semibold text-amber-400 cursor-pointer"
              title="Tự động Zoom cận cảnh khuôn mặt / nửa thân trên"
            >
              Zoom Cận Mặt
            </button>

            <button
              onClick={handleResetCamera}
              className="p-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 cursor-pointer"
              title="Đặt lại vị trí Camera (Reset Zoom/Pan)"
            >
              <RotateCcw className="w-4 h-4" strokeWidth={1.75} />
            </button>

            <button
              onClick={handleSnapshotPng}
              className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-[11px] font-semibold text-slate-200 cursor-pointer"
              title="Chụp ảnh PNG 1 frame hiện tại (không nền)"
            >
              <Camera className="w-3.5 h-3.5 text-amber-400" strokeWidth={1.75} />
              <span className="hidden sm:inline">Chụp PNG</span>
            </button>
          </div>
        </div>

        {/* Center Canvas Container */}
        <div
          className="flex-1 relative flex items-center justify-center overflow-hidden select-none cursor-grab active:cursor-grabbing"
          style={getViewportBgStyle()}
          onMouseDown={handleCanvasMouseDown}
          onMouseMove={handleCanvasMouseMove}
          onMouseUp={handleCanvasMouseUp}
          onMouseLeave={handleCanvasMouseUp}
          onWheel={handleCanvasWheel}
        >
          {/* WebGL Canvas */}
          <canvas
            ref={canvasRef}
            width={currentResolution.width}
            height={currentResolution.height}
            className="max-w-full max-h-full object-contain shadow-2xl border border-slate-800/60"
          />

          {/* 1:1 Center Crop Guide Overlay */}
          {showCropGuide && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <div className="w-72 h-72 sm:w-96 sm:h-96 border-2 border-dashed border-amber-400/80 rounded-2xl shadow-[0_0_0_9999px_rgba(0,0,0,0.45)] flex items-end justify-center pb-2">
                <span className="px-2 py-0.5 rounded bg-slate-950/90 text-amber-400 text-[10px] font-semibold">
                  Vùng Cắt Vuông 1:1 (Totem / Avatar)
                </span>
              </div>
            </div>
          )}

          {/* Loading Overlay */}
          {isLoadingModel && (
            <div className="absolute inset-0 bg-slate-950/80 backdrop-blur-sm flex flex-col items-center justify-center gap-3 z-20">
              <RefreshCw className="w-8 h-8 text-amber-400 animate-spin" strokeWidth={1.75} />
              <div className="text-center">
                <p className="text-sm font-bold text-white">Đang tải Model Spine 2D từ Nikke-DB...</p>
                <p className="text-xs text-slate-400 mt-0.5">
                  Đang nạp <span className="font-mono text-amber-400">.skel</span>,{' '}
                  <span className="font-mono text-amber-400">.atlas</span> và Texture PNG độ phân giải cao
                </p>
              </div>
            </div>
          )}

          {/* Export Progress Modal Overlay */}
          {isExporting && (
            <div className="absolute inset-0 bg-slate-950/85 backdrop-blur-md flex flex-col items-center justify-center p-6 z-30">
              <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-4 text-center">
                <Film className="w-10 h-10 text-amber-400 mx-auto animate-pulse" strokeWidth={1.75} />
                <div className="space-y-1">
                  <h3 className="text-base font-bold text-white">{exportProgress.label}</h3>
                  <p className="text-xs text-slate-400">
                    Đang kết xuất từng frame chuẩn xác 100% ở {exportFps} FPS (Không rớt frame, giữ nguyên kênh Alpha trong suốt)
                  </p>
                </div>

                {/* Progress Bar */}
                <div className="w-full h-3 bg-slate-950 rounded-full overflow-hidden border border-slate-800">
                  <div
                    className="h-full bg-gradient-to-r from-amber-400 to-orange-500 transition-all duration-100"
                    style={{
                      width: `${
                        exportProgress.total > 0
                          ? Math.round((exportProgress.current / exportProgress.total) * 100)
                          : 0
                      }%`,
                    }}
                  />
                </div>

                <div className="flex items-center justify-between text-xs font-mono text-slate-300">
                  <span>
                    Frame: {exportProgress.current} / {exportProgress.total}
                  </span>
                  <span className="text-amber-400 font-bold">
                    {exportProgress.total > 0
                      ? Math.round((exportProgress.current / exportProgress.total) * 100)
                      : 0}
                    %
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* Error Banner */}
          {loadError && !isLoadingModel && (
            <div className="absolute top-4 left-4 right-4 max-w-lg mx-auto bg-rose-950/90 border border-rose-500/40 text-rose-200 p-3.5 rounded-2xl shadow-xl flex items-start gap-3 z-20">
              <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" strokeWidth={1.75} />
              <div className="text-xs space-y-1 flex-1">
                <p className="font-bold text-rose-100">Không thể nạp dữ liệu Model</p>
                <p className="text-rose-300/90 leading-relaxed">{loadError}</p>
              </div>
              <button
                onClick={() => setLoadError(null)}
                className="text-xs text-rose-300 hover:text-white font-bold px-2"
              >
                ✕
              </button>
            </div>
          )}

          {/* Export Success Toast */}
          {exportSuccessMsg && !isExporting && (
            <div className="absolute bottom-4 left-4 right-4 max-w-xl mx-auto bg-emerald-950/95 border border-emerald-500/40 text-emerald-200 p-3.5 rounded-2xl shadow-xl flex items-center gap-3 z-20">
              <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" strokeWidth={1.75} />
              <p className="text-xs font-medium flex-1 leading-relaxed">{exportSuccessMsg}</p>
              <button
                onClick={() => setExportSuccessMsg(null)}
                className="text-xs text-emerald-300 hover:text-white font-bold px-2 cursor-pointer"
              >
                Đóng
              </button>
            </div>
          )}

          {/* Bottom-left Hint */}
          <div className="pointer-events-none absolute bottom-3 left-3 px-2.5 py-1 rounded-lg bg-slate-950/75 border border-slate-800/80 text-[11px] text-slate-400 flex items-center gap-2">
            <span>Kéo chuột: Di chuyển (Pan)</span>
            <span>•</span>
            <span>Cuộn chuột: Zoom ({Math.round(cameraZoom * 100)}%)</span>
          </div>
        </div>

        {/* Bottom Animation Timeline & Playback Bar */}
        <div className="p-3.5 border-t border-slate-800/80 bg-slate-900/80 backdrop-blur-md space-y-2.5">
          <div className="flex flex-wrap items-center gap-3">
            {/* Play / Pause */}
            <button
              onClick={() => setIsPlaying((p) => !p)}
              className="w-9 h-9 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 flex items-center justify-center shadow-md shadow-amber-500/10 transition-all cursor-pointer shrink-0"
              title={isPlaying ? 'Tạm dừng' : 'Phát tiếp'}
            >
              {isPlaying ? (
                <Pause className="w-4 h-4" strokeWidth={2} />
              ) : (
                <Play className="w-4 h-4 ml-0.5" strokeWidth={2} />
              )}
            </button>

            {/* Loop Toggle */}
            <button
              onClick={handleToggleLoop}
              className={`p-2 rounded-xl border text-xs transition-all cursor-pointer ${
                isLooping
                  ? 'bg-amber-500/15 border-amber-500/40 text-amber-400'
                  : 'bg-slate-950 border-slate-800 text-slate-500 hover:text-slate-300'
              }`}
              title="Lặp lại vòng lặp (Loop)"
            >
              <Repeat className="w-4 h-4" strokeWidth={1.75} />
            </button>

            {/* Animation Selector Dropdown */}
            <div className="flex items-center gap-2 min-w-[180px]">
              <span className="text-xs text-slate-400 font-medium">Animation:</span>
              <select
                value={currentAnim}
                onChange={(e) => handleSelectAnimation(e.target.value)}
                className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs font-semibold text-amber-400 focus:outline-none focus:border-amber-500/50"
              >
                {animations.map((a) => (
                  <option key={a.name} value={a.name}>
                    {a.name} ({a.duration.toFixed(2)}s • {Math.round(a.duration * exportFps)} frames @{exportFps}fps)
                  </option>
                ))}
              </select>
            </div>

            {/* Timeline Scrubber */}
            <div className="flex-1 flex items-center gap-3 min-w-[200px]">
              <input
                type="range"
                min={0}
                max={Math.max(0.01, animDuration)}
                step={0.01}
                value={Math.min(animTime, animDuration || 0.01)}
                onChange={(e) => handleTimelineScrub(parseFloat(e.target.value))}
                className="flex-1 accent-amber-400 cursor-pointer h-1.5 bg-slate-800 rounded-lg"
              />
              <span className="text-xs font-mono text-slate-300 w-28 text-right shrink-0">
                {animTime.toFixed(2)}s / {animDuration.toFixed(2)}s
              </span>
            </div>

            {/* Playback Speed */}
            <div className="flex items-center gap-1.5">
              <span className="text-[11px] text-slate-400">Tốc độ:</span>
              {[0.5, 1.0, 1.5, 2.0].map((spd) => (
                <button
                  key={spd}
                  onClick={() => setPlaybackSpeed(spd)}
                  className={`px-2 py-1 rounded-lg text-[11px] font-mono cursor-pointer ${
                    playbackSpeed === spd
                      ? 'bg-amber-400 text-slate-950 font-bold'
                      : 'bg-slate-950 text-slate-400 hover:text-slate-200 border border-slate-800'
                  }`}
                >
                  {spd}x
                </button>
              ))}
            </div>
          </div>
        </div>
      </main>

      {/* =====================================================================
          RIGHT SIDEBAR: EXPORT STUDIO (60FPS ZIP / WEBM ALPHA / TOTEM) & LAYERS
         ===================================================================== */}
      <aside className="w-full lg:w-96 border-t lg:border-t-0 lg:border-l border-slate-800/80 bg-slate-900/50 flex flex-col shrink-0">
        {/* Right Sidebar Navigation Tabs */}
        <div className="grid grid-cols-3 border-b border-slate-800/80 bg-slate-950/60 p-1.5 gap-1">
          <button
            onClick={() => setActiveTabRight('export')}
            className={`flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeTabRight === 'export'
                ? 'bg-amber-400 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Download className="w-3.5 h-3.5" strokeWidth={2} />
            <span>Xuất File</span>
          </button>

          <button
            onClick={() => setActiveTabRight('layers')}
            className={`flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeTabRight === 'layers'
                ? 'bg-amber-400 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="w-3.5 h-3.5" strokeWidth={2} />
            <span>Layers ({slotList.length})</span>
          </button>

          <button
            onClick={() => setActiveTabRight('custom')}
            className={`flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeTabRight === 'custom'
                ? 'bg-amber-400 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <FolderOpen className="w-3.5 h-3.5" strokeWidth={2} />
            <span>Nạp File</span>
          </button>
        </div>

        {/* TAB 1: EXPORT STUDIO */}
        {activeTabRight === 'export' && (
          <div className="flex-1 overflow-y-auto p-4 space-y-5">
            {/* Resolution & FPS Settings */}
            <div className="bg-slate-950/70 border border-slate-800/90 rounded-2xl p-3.5 space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-amber-400">
                <Sliders className="w-4 h-4" strokeWidth={1.75} />
                <span>Cấu Hình Kết Xuất (Render Config)</span>
              </div>

              {/* Output Resolution */}
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 mb-1">
                  Độ phân giải Canvas (Resolution):
                </label>
                <select
                  value={exportResId}
                  onChange={(e) => setExportResId(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500/50"
                >
                  {RESOLUTION_PRESETS.map((res) => (
                    <option key={res.id} value={res.id}>
                      {res.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Target FPS (Default 60 FPS) */}
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 mb-1">
                  Tốc độ khung hình xuất (Target FPS):
                </label>
                <div className="grid grid-cols-4 gap-1.5">
                  {[20, 30, 60, 120].map((fpsVal) => (
                    <button
                      key={fpsVal}
                      onClick={() => setExportFps(fpsVal)}
                      className={`py-1.5 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
                        exportFps === fpsVal
                          ? 'bg-amber-400/20 border-amber-400 text-amber-300'
                          : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {fpsVal} FPS
                    </button>
                  ))}
                </div>
              </div>

              {/* Premultiplied Alpha Switch */}
              <div className="flex items-center justify-between pt-1">
                <span className="text-xs text-slate-300">Premultiplied Alpha (Khử viền đen):</span>
                <button
                  onClick={() => setUsePma((v) => !v)}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold cursor-pointer ${
                    usePma
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                      : 'bg-slate-900 text-slate-400 border border-slate-800'
                  }`}
                >
                  {usePma ? 'Đang Bật' : 'Đang Tắt'}
                </button>
              </div>

              {/* Summary Box */}
              <div className="p-2.5 rounded-xl bg-slate-900/90 border border-slate-800/80 text-[11px] text-slate-300 space-y-1">
                <div className="flex justify-between">
                  <span className="text-slate-400">Animation đang chọn:</span>
                  <span className="font-mono font-bold text-amber-400">{currentAnim || 'idle'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Thời lượng chu kỳ:</span>
                  <span className="font-mono">{animDuration.toFixed(2)} giây</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Tổng số frame ở {exportFps} FPS:</span>
                  <span className="font-mono font-bold text-emerald-400">
                    {Math.max(1, Math.round(animDuration * exportFps))} frames (.png)
                  </span>
                </div>
              </div>
            </div>

            {/* PRIMARY FEATURE 1: EXPORT 60FPS PNG SEQUENCE (.ZIP) */}
            <div className="bg-gradient-to-br from-amber-500/10 via-slate-900 to-slate-950 border border-amber-500/30 rounded-2xl p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-amber-400/20 text-amber-300 mb-1">
                    CHUẨN 60 FPS • KHÔNG NỀN
                  </span>
                  <h4 className="text-sm font-bold text-white">
                    1. Xuất Chuỗi Frame PNG (.ZIP)
                  </h4>
                  <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                    Trích xuất từng frame (<code className="text-amber-300">frame_0000.png</code>...) bằng phương pháp Deterministic Step (không rớt bất kỳ frame nào), giữ nguyên kênh Alpha trong suốt.
                  </p>
                </div>
              </div>

              <button
                onClick={handleExportZipFrames}
                disabled={isExporting || isLoadingModel}
                className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-amber-400 to-amber-500 hover:from-amber-300 hover:to-amber-400 disabled:opacity-50 text-slate-950 font-extrabold text-xs flex items-center justify-center gap-2 shadow-lg shadow-amber-500/20 transition-all cursor-pointer"
              >
                <Archive className="w-4 h-4" strokeWidth={2} />
                <span>
                  Xuất File .ZIP ({Math.max(1, Math.round(animDuration * exportFps))} PNG Frames @ {exportFps} FPS)
                </span>
              </button>
            </div>

            {/* PRIMARY FEATURE 2: EXPORT TRANSPARENT VIDEO (.WEBM ALPHA) */}
            <div className="bg-gradient-to-br from-sky-500/10 via-slate-900 to-slate-950 border border-sky-500/30 rounded-2xl p-4 space-y-3">
              <div>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-sky-400/20 text-sky-300 mb-1">
                  WEBM VP9 ALPHA • TRANSPARENT VIDEO
                </span>
                <h4 className="text-sm font-bold text-white">
                  2. Xuất Video Không Nền (.WebM)
                </h4>
                <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                  Ghi trực tiếp từ bộ đệm WebGL sang định dạng video <code className="text-sky-300">.webm</code> có kênh Alpha trong suốt ở {exportFps} FPS (dùng cho Premiere, OBS, Wallpaper, TotemConverter).
                </p>
              </div>

              <button
                onClick={handleExportTransparentVideo}
                disabled={isExporting || isLoadingModel}
                className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-sky-400 to-cyan-500 hover:from-sky-300 hover:to-cyan-400 disabled:opacity-50 text-slate-950 font-extrabold text-xs flex items-center justify-center gap-2 shadow-lg shadow-sky-500/20 transition-all cursor-pointer"
              >
                <Film className="w-4 h-4" strokeWidth={2} />
                <span>Xuất Video Không Nền (.WebM {exportFps} FPS)</span>
              </button>
            </div>

            {/* PRIMARY FEATURE 3: DIRECT MINECRAFT TOTEM SPRITESHEET */}
            <div className="bg-gradient-to-br from-emerald-500/10 via-slate-900 to-slate-950 border border-emerald-500/30 rounded-2xl p-4 space-y-3">
              <div>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-emerald-400/20 text-emerald-300 mb-1">
                  TÍCH HỢP CUSTOM TOTEM MOD
                </span>
                <h4 className="text-sm font-bold text-white">
                  3. Xuất Thẳng Minecraft Totem PNG
                </h4>
                <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                  Tự động đóng gói chuỗi frame thành 1 file ảnh <code className="text-emerald-300">Grid Atlas / Vertical Strip PNG</code> duy nhất để dùng ngay trong Minecraft!
                </p>
              </div>

              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-slate-300">Kích thước mỗi frame:</span>
                <div className="flex gap-1">
                  {[128, 256, 512].map((sz) => (
                    <button
                      key={sz}
                      onClick={() => setTotemFrameSize(sz)}
                      className={`px-2.5 py-1 rounded-lg text-[11px] font-bold cursor-pointer ${
                        totemFrameSize === sz
                          ? 'bg-emerald-400 text-slate-950'
                          : 'bg-slate-950 text-slate-400 border border-slate-800'
                      }`}
                    >
                      {sz}px
                    </button>
                  ))}
                </div>
              </div>

              <div className="grid grid-cols-1 gap-2">
                <button
                  onClick={() => handleExportMinecraftTotem(false)}
                  disabled={isExporting || isLoadingModel}
                  className="w-full py-2 px-3 rounded-xl bg-emerald-400 hover:bg-emerald-300 disabled:opacity-50 text-slate-950 font-extrabold text-xs flex items-center justify-center gap-2 transition-all cursor-pointer"
                >
                  <Download className="w-4 h-4" strokeWidth={2} />
                  <span>Tải File Totem Spritesheet (.PNG)</span>
                </button>

                {onSendToTotemGallery && (
                  <button
                    onClick={() => handleExportMinecraftTotem(true)}
                    disabled={isExporting || isLoadingModel}
                    className="w-full py-2 px-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-emerald-500/40 text-emerald-300 font-bold text-xs flex items-center justify-center gap-2 transition-all cursor-pointer"
                  >
                    <Sparkles className="w-3.5 h-3.5" strokeWidth={1.75} />
                    <span>Đăng Luôn Lên Thư Viện TotemHub</span>
                  </button>
                )}
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: LAYER / SLOT INSPECTOR (BD2-L2D-Viewer Feature) */}
        {activeTabRight === 'layers' && (
          <div className="flex-1 flex flex-col overflow-hidden p-4 space-y-3">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200">
                  Kiểm Tra Lớp / Bộ Phận ({slotList.length} Slots)
                </span>
                <button
                  onClick={() => {
                    hiddenSlotsRef.current.clear();
                    const ctx = spineCtxRef.current;
                    if (ctx.skeleton?.slots) {
                      ctx.skeleton.slots.forEach((s) => (s.color.a = 1));
                    }
                    setSlotList((prev) => [...prev]);
                    renderCurrentFrame();
                  }}
                  className="text-[11px] text-amber-400 hover:underline cursor-pointer"
                >
                  Hiện tất cả
                </button>
              </div>
              <p className="text-[11px] text-slate-400">
                Bật/tắt từng lớp (vũ khí, hiệu ứng khói, nền phụ, phụ kiện...) trước khi xuất video hoặc frame PNG.
              </p>
              <input
                type="text"
                value={slotSearch}
                onChange={(e) => setSlotSearch(e.target.value)}
                placeholder="Lọc tên layer / slot (vd: weapon, hair, bg, effect)..."
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
              />
            </div>

            <div className="flex-1 overflow-y-auto space-y-1 pr-1">
              {filteredSlots.map((slot) => {
                const isHidden = hiddenSlotsRef.current.has(slot.name);
                return (
                  <button
                    key={slot.name}
                    onClick={() => handleToggleSlot(slot.name)}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs border transition-all cursor-pointer ${
                      isHidden
                        ? 'bg-slate-950/40 border-slate-900 text-slate-600'
                        : 'bg-slate-950/90 border-slate-800/80 text-slate-200 hover:border-slate-700'
                    }`}
                  >
                    <div className="truncate text-left pr-2">
                      <span className="font-mono font-medium">{slot.name}</span>
                    </div>
                    {isHidden ? (
                      <EyeOff className="w-4 h-4 text-rose-400 shrink-0" strokeWidth={1.75} />
                    ) : (
                      <Eye className="w-4 h-4 text-emerald-400 shrink-0" strokeWidth={1.75} />
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* TAB 3: CUSTOM FILE / ID LOADER */}
        {activeTabRight === 'custom' && (
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {/* Load local Spine files */}
            <div className="bg-slate-950/80 border border-slate-800 rounded-2xl p-4 space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-amber-400">
                <Upload className="w-4 h-4" strokeWidth={1.75} />
                <span>Nạp File Spine Từ Máy Tính (Local)</span>
              </div>
              <p className="text-[11px] text-slate-400 leading-relaxed">
                Hỗ trợ mọi model Spine 4.0 & 4.1 (Nikke, Brown Dust 2, Blue Archive...). Hãy bôi đen chọn cùng lúc bộ 3 file: <strong className="text-slate-200">.skel (hoặc .json)</strong> + <strong className="text-slate-200">.atlas</strong> + <strong className="text-slate-200">.png</strong>.
              </p>

              <label className="w-full py-6 px-4 border-2 border-dashed border-slate-700 hover:border-amber-400/60 rounded-2xl bg-slate-900/50 flex flex-col items-center justify-center gap-2 cursor-pointer transition-all">
                <FolderOpen className="w-7 h-7 text-amber-400" strokeWidth={1.75} />
                <span className="text-xs font-bold text-slate-200">
                  Chọn các file (.skel, .atlas, .png)
                </span>
                <span className="text-[10px] text-slate-500">
                  Giữ phím Ctrl hoặc Shift để chọn nhiều file cùng lúc
                </span>
                <input
                  type="file"
                  multiple
                  accept=".skel,.json,.atlas,.txt,.png,.webp"
                  onChange={handleLocalFilesUpload}
                  className="hidden"
                />
              </label>
            </div>

            {/* Load arbitrary Nikke Character ID */}
            <form
              onSubmit={handleLoadCustomCharId}
              className="bg-slate-950/80 border border-slate-800 rounded-2xl p-4 space-y-3"
            >
              <div className="flex items-center gap-2 text-xs font-bold text-sky-400">
                <Info className="w-4 h-4" strokeWidth={1.75} />
                <span>Nhập Mã ID Nhân Vật Bất Kỳ (Nikke-DB)</span>
              </div>
              <p className="text-[11px] text-slate-400 leading-relaxed">
                Nếu nhân vật mới ra mắt chưa có trong danh sách gợi ý, bạn chỉ cần nhập mã ID (ví dụ: <code className="text-sky-300">c010</code>, <code className="text-sky-300">c350</code>, <code className="text-sky-300">c510</code>) để tải trực tiếp từ kho dữ liệu Nikke-DB.
              </p>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={customIdInput}
                  onChange={(e) => setCustomIdInput(e.target.value)}
                  placeholder="Nhập mã (vd: c220, c907)..."
                  className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-sky-500/50"
                />
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 font-bold text-xs cursor-pointer"
                >
                  Tải ID
                </button>
              </div>
            </form>
          </div>
        )}
      </aside>
    </div>
  );
}
