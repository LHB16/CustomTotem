// Comprehensive Nikke Live2D / Spine 2D Character & Costume Catalog
// Primary CDN: NKAS / Nikke-DB Cloudflare Pages Storage (https://nkas-l2d.pages.dev)

export const NIKKE_L2D_BASE_URL = 'https://nkas-l2d.pages.dev/characters';
export const NKAS_CHARACTERS_JSON_URL = 'https://nkas-l2d.pages.dev/characters.json';
export const NKAS_BURSTS_JSON_URL = 'https://nkas-l2d.pages.dev/bursts.json';
export const LEGACY_GITHUB_L2D_URL = 'https://raw.githubusercontent.com/Nikke-db/Nikke-db.github.io/main/l2d';

export const NIKKE_MANUFACTURERS = [
  { id: 'all', label: 'Tất Cả' },
  { id: 'Pilgrim', label: 'Pilgrim' },
  { id: 'Elysion', label: 'Elysion' },
  { id: 'Missilis', label: 'Missilis' },
  { id: 'Tetra', label: 'Tetra' },
  { id: 'Abnormal', label: 'Abnormal' },
];

export const NIKKE_POSES = [
  { id: 'fb', label: 'Standing (Idle)', desc: 'Tư thế đứng toàn thân sảnh chính' },
  { id: 'aim', label: 'Aim (Ngắm bắn)', desc: 'Tư thế chiến đấu ngắm bắn' },
  { id: 'cover', label: 'Cover (Nấp)', desc: 'Tư thế ẩn nấp chiến đấu' },
];

/**
 * Builds an ordered list of candidate Spine asset URLs (.atlas, .skel, .png)
 * Primary format on nkas-l2d.pages.dev:
 *   https://nkas-l2d.pages.dev/characters/{id}_{skin}/{idle|aim|cover}/{id}_{skin}.{atlas|skel|png}
 */
export function getNikkeSpineUrlCandidates(charId, skinCode = '00', pose = 'fb') {
  const raw = String(charId || 'c220').trim().toLowerCase();
  let baseCharId = raw;
  let resolvedSkin = String(skinCode || '00').padStart(2, '0');

  if (raw.includes('_')) {
    const parts = raw.split('_');
    baseCharId = parts[0];
    if (!skinCode || skinCode === '00') {
      resolvedSkin = parts[1] || '00';
    }
  }

  const fullId = `${baseCharId}_${resolvedSkin}`;
  const poseFolder = pose === 'aim' ? 'aim' : pose === 'cover' ? 'cover' : 'idle';

  const candidates = [];

  // 1. Primary NKAS Cloudflare CDN: /characters/{fullId}/{poseFolder}/{fullId}.{ext}
  const nkasBase = `${NIKKE_L2D_BASE_URL}/${fullId}/${poseFolder}`;
  candidates.push({
    basePath: `${nkasBase}/`,
    skelUrl: `${nkasBase}/${fullId}.skel`,
    atlasUrl: `${nkasBase}/${fullId}.atlas`,
    pngUrl: `${nkasBase}/${fullId}.png`,
    fileStem: fullId,
  });

  // 2. Variant NKAS naming for aim/cover if file stem includes _aim_ or _cover_
  if (poseFolder === 'aim' || poseFolder === 'cover') {
    const poseStem = `${baseCharId}_${poseFolder}_${resolvedSkin}`;
    candidates.push({
      basePath: `${nkasBase}/`,
      skelUrl: `${nkasBase}/${poseStem}.skel`,
      atlasUrl: `${nkasBase}/${poseStem}.atlas`,
      pngUrl: `${nkasBase}/${poseStem}.png`,
      fileStem: poseStem,
    });
    const poseStem2 = `${fullId}_${poseFolder}`;
    candidates.push({
      basePath: `${nkasBase}/`,
      skelUrl: `${nkasBase}/${poseStem2}.skel`,
      atlasUrl: `${nkasBase}/${poseStem2}.atlas`,
      pngUrl: `${nkasBase}/${poseStem2}.png`,
      fileStem: poseStem2,
    });
  }

  // 3. Fallback if skinCode !== '00' was requested but only '00' exists or vice versa
  if (resolvedSkin !== '00') {
    const defaultFullId = `${baseCharId}_00`;
    const defBase = `${NIKKE_L2D_BASE_URL}/${defaultFullId}/${poseFolder}`;
    candidates.push({
      basePath: `${defBase}/`,
      skelUrl: `${defBase}/${defaultFullId}.skel`,
      atlasUrl: `${defBase}/${defaultFullId}.atlas`,
      pngUrl: `${defBase}/${defaultFullId}.png`,
      fileStem: defaultFullId,
      isFallbackDefaultSkin: true,
    });
  }

  // 4. Legacy GitHub Mirror fallback
  if (poseFolder === 'idle') {
    const ghBase = `${LEGACY_GITHUB_L2D_URL}/${fullId}`;
    candidates.push({
      basePath: `${ghBase}/`,
      skelUrl: `${ghBase}/${fullId}.skel`,
      atlasUrl: `${ghBase}/${fullId}.atlas`,
      pngUrl: `${ghBase}/${fullId}.png`,
      fileStem: fullId,
    });
  } else {
    const ghStem = `${baseCharId}_${poseFolder}_${resolvedSkin}`;
    const ghBase = `${LEGACY_GITHUB_L2D_URL}/${fullId}/${poseFolder}`;
    candidates.push({
      basePath: `${ghBase}/`,
      skelUrl: `${ghBase}/${ghStem}.skel`,
      atlasUrl: `${ghBase}/${ghStem}.atlas`,
      pngUrl: `${ghBase}/${ghStem}.png`,
      fileStem: ghStem,
    });
  }

  return candidates;
}

export function getNikkeSpineUrls(charId, skinCode = '00', pose = 'fb') {
  return getNikkeSpineUrlCandidates(charId, skinCode, pose)[0];
}

/**
 * Dynamically fetches https://nkas-l2d.pages.dev/characters.json and merges all discovered
 * characters and skin codes into the catalog so 100% of live CDN models work seamlessly.
 */
export async function fetchAndMergeNkasCatalog(baseCatalog = NIKKE_CHARACTERS) {
  try {
    const res = await fetch(NKAS_CHARACTERS_JSON_URL);
    if (!res.ok) return baseCatalog;
    const data = await res.json();

    // Clone base catalog
    const catalogMap = new Map();
    const discoveredSkinsMap = new Map(); // baseId -> Map<skinCode, skinName>

    baseCatalog.forEach((c) => {
      catalogMap.set(c.id.toLowerCase(), {
        ...c,
        skins: [...(c.skins || [{ code: '00', name: 'Default' }])],
      });
    });

    const addEntry = (rawFullId, rawLabel) => {
      if (!rawFullId || typeof rawFullId !== 'string') return;
      const clean = rawFullId.trim();
      const match = clean.match(/^([a-zA-Z0-9]+?)(?:_([0-9a-zA-Z]+))?$/);
      if (!match) return;

      const baseId = match[1].toLowerCase();
      const skinCode = match[2] || '00';
      const labelStr = typeof rawLabel === 'string' && rawLabel.trim() ? rawLabel.trim() : clean;

      if (!discoveredSkinsMap.has(baseId)) {
        discoveredSkinsMap.set(baseId, new Map());
      }
      discoveredSkinsMap.get(baseId).set(skinCode, labelStr);

      if (!catalogMap.has(baseId)) {
        catalogMap.set(baseId, {
          id: baseId,
          name: labelStr,
          manufacturer: baseId.startsWith('c') ? 'Nikke' : 'Other',
          squad: 'NKAS Database',
          element: 'Unknown',
          weapon: 'L2D',
          accent: '#fbbf24',
          skins: [],
        });
      }
    };

    if (Array.isArray(data)) {
      data.forEach((item) => {
        if (typeof item === 'string') {
          addEntry(item, item);
        } else if (Array.isArray(item)) {
          addEntry(item[0], item[1] || item[0]);
        } else if (item && typeof item === 'object') {
          const id = item.id || item.code || item.character_id || item.key;
          const name = item.name || item.title || item.label || id;
          if (Array.isArray(item.skins)) {
            item.skins.forEach((sk) => {
              const skCode = typeof sk === 'string' ? sk : sk.code || sk.id || '00';
              const skName = typeof sk === 'object' ? sk.name || sk.label || name : name;
              addEntry(`${id}_${skCode}`, skName);
            });
          } else {
            addEntry(id, name);
          }
        }
      });
    } else if (data && typeof data === 'object') {
      Object.entries(data).forEach(([key, val]) => {
        if (typeof val === 'string') {
          addEntry(key, val);
        } else if (val && typeof val === 'object') {
          addEntry(val.id || key, val.name || val.title || key);
        } else {
          addEntry(key, key);
        }
      });
    }

    // Reconcile discovered skins with catalog entries
    for (const [baseId, skinMap] of discoveredSkinsMap.entries()) {
      const entry = catalogMap.get(baseId);
      if (!entry || skinMap.size === 0) continue;

      const oldSkinsByCode = new Map((entry.skins || []).map((s) => [s.code, s.name]));
      const mergedSkins = [];
      for (const [code, remoteLabel] of skinMap.entries()) {
        const knownName = oldSkinsByCode.get(code);
        const displayName =
          remoteLabel && remoteLabel !== `${baseId}_${code}` && remoteLabel !== entry.name
            ? remoteLabel
            : knownName || (code === '00' ? 'Default' : `Costume ${code}`);
        mergedSkins.push({ code, name: displayName });
      }
      mergedSkins.sort((a, b) => a.code.localeCompare(b.code));
      entry.skins = mergedSkins;
    }

    return Array.from(catalogMap.values());
  } catch (err) {
    console.warn('Không thể tải danh mục động từ nkas-l2d.pages.dev, sử dụng danh mục mặc định:', err);
    return baseCatalog;
  }
}

export const NIKKE_CHARACTERS = [
  // PILGRIM
  {
    id: 'c220',
    name: 'Dorothy',
    manufacturer: 'Pilgrim',
    squad: 'Inherit',
    element: 'Water',
    weapon: 'AR',
    accent: '#f472b6',
    skins: [
      { code: '00', name: 'Default (Eden Wing)' },
      { code: '01', name: 'Nostalgia (Gacha Skin)' },
    ],
  },
  {
    id: 'c907',
    name: 'Red Hood',
    manufacturer: 'Pilgrim',
    squad: 'Goddess',
    element: 'Iron',
    weapon: 'SR',
    accent: '#ef4444',
    skins: [
      { code: '00', name: 'Default (Wolf Spirit)' },
      { code: '01', name: 'Nonsense Red (Dress)' },
    ],
  },
  {
    id: 'c232',
    name: 'Modernia',
    manufacturer: 'Pilgrim',
    squad: 'Heretic',
    element: 'Fire',
    weapon: 'MG',
    accent: '#38bdf8',
    skins: [
      { code: '00', name: 'Default (Machina)' },
      { code: '01', name: 'First Affection (Shirt)' },
      { code: '02', name: 'Second Affection (Queen)' },
    ],
  },
  {
    id: 'c070',
    name: 'Scarlet',
    manufacturer: 'Pilgrim',
    squad: 'Pioneer',
    element: 'Electric',
    weapon: 'AR',
    accent: '#a855f7',
    skins: [
      { code: '00', name: 'Default (Fleetly Fading)' },
    ],
  },
  {
    id: 'c073',
    name: 'Scarlet: Black Shadow',
    manufacturer: 'Pilgrim',
    squad: 'Goddess',
    element: 'Wind',
    weapon: 'RL',
    accent: '#dc2626',
    skins: [
      { code: '00', name: 'Default (Past Sword)' },
      { code: '01', name: 'Longing Flower' },
    ],
  },
  {
    id: 'c410',
    name: 'Crown',
    manufacturer: 'Pilgrim',
    squad: 'White Knights',
    element: 'Iron',
    weapon: 'MG',
    accent: '#fbbf24',
    skins: [
      { code: '00', name: 'Default (Royal)' },
      { code: '01', name: 'Naked King' },
    ],
  },
  {
    id: 'c510',
    name: 'Cinderella',
    manufacturer: 'Pilgrim',
    squad: 'Old Tales',
    element: 'Electric',
    weapon: 'RL',
    accent: '#e2e8f0',
    skins: [
      { code: '00', name: 'Default (Glass Slippers)' },
      { code: '01', name: 'Glass Princess' },
    ],
  },
  {
    id: 'c071',
    name: 'Snow White',
    manufacturer: 'Pilgrim',
    squad: 'Pioneer',
    element: 'Iron',
    weapon: 'AR',
    accent: '#f8fafc',
    skins: [
      { code: '00', name: 'Default (Seven Dwarves)' },
      { code: '01', name: 'White Knight' },
    ],
  },
  {
    id: 'c072',
    name: 'Rapunzel',
    manufacturer: 'Pilgrim',
    squad: 'Pioneer',
    element: 'Iron',
    weapon: 'RL',
    accent: '#fde047',
    skins: [
      { code: '00', name: 'Default (Saint)' },
    ],
  },
  {
    id: 'c130',
    name: 'Harran',
    manufacturer: 'Pilgrim',
    squad: 'Inherit',
    element: 'Electric',
    weapon: 'SR',
    accent: '#9333ea',
    skins: [
      { code: '00', name: 'Default (Witch)' },
      { code: '01', name: 'Banquet Witch' },
    ],
  },
  {
    id: 'c131',
    name: 'Isabel',
    manufacturer: 'Pilgrim',
    squad: 'Inherit',
    element: 'Electric',
    weapon: 'SG',
    accent: '#ec4899',
    skins: [
      { code: '00', name: 'Default (Obsession)' },
      { code: '01', name: 'Honeymoon Party' },
    ],
  },
  {
    id: 'c132',
    name: 'Noah',
    manufacturer: 'Pilgrim',
    squad: 'Inherit',
    element: 'Wind',
    weapon: 'RL',
    accent: '#f43f5e',
    skins: [
      { code: '00', name: 'Default (Shield)' },
    ],
  },
  {
    id: 'c231',
    name: 'Nihilister',
    manufacturer: 'Pilgrim',
    squad: 'Heretic',
    element: 'Fire',
    weapon: 'SR',
    accent: '#f97316',
    skins: [
      { code: '00', name: 'Default (Dragon)' },
    ],
  },

  // ELYSION
  {
    id: 'c010',
    name: 'Rapi',
    manufacturer: 'Elysion',
    squad: 'Counters',
    element: 'Fire',
    weapon: 'AR',
    accent: '#ef4444',
    skins: [
      { code: '00', name: 'Default (Counters)' },
      { code: '01', name: 'White Promise' },
      { code: '02', name: 'Classic Vacation (Swimsuit)' },
    ],
  },
  {
    id: 'c011',
    name: 'Neon',
    manufacturer: 'Elysion',
    squad: 'Counters',
    element: 'Fire',
    weapon: 'SG',
    accent: '#3b82f6',
    skins: [
      { code: '00', name: 'Default (Firepower)' },
      { code: '01', name: 'Blind Bullet' },
    ],
  },
  {
    id: 'c110',
    name: 'Helm',
    manufacturer: 'Elysion',
    squad: 'Aegis',
    element: 'Water',
    weapon: 'SR',
    accent: '#2563eb',
    skins: [
      { code: '00', name: 'Default (Admire)' },
      { code: '01', name: 'Chandelier (Dress)' },
    ],
  },
  {
    id: 'c113',
    name: 'Helm: Aquamarine',
    manufacturer: 'Elysion',
    squad: 'Aegis',
    element: 'Iron',
    weapon: 'AR',
    accent: '#38bdf8',
    skins: [
      { code: '00', name: 'Default (Summer)' },
    ],
  },
  {
    id: 'c160',
    name: 'Privaty',
    manufacturer: 'Elysion',
    squad: 'Triangle',
    element: 'Water',
    weapon: 'AR',
    accent: '#06b6d4',
    skins: [
      { code: '00', name: 'Default (Triangle)' },
      { code: '01', name: 'Government Grunt' },
      { code: '02', name: 'Banquet Princess' },
    ],
  },
  {
    id: 'c320',
    name: 'D',
    manufacturer: 'Elysion',
    squad: 'Perilous Siege',
    element: 'Wind',
    weapon: 'SMG',
    accent: '#64748b',
    skins: [
      { code: '00', name: 'Default (Assassin)' },
    ],
  },
  {
    id: 'c322',
    name: 'D: Killer Wife',
    manufacturer: 'Elysion',
    squad: 'Perilous Siege',
    element: 'Fire',
    weapon: 'SR',
    accent: '#f43f5e',
    skins: [
      { code: '00', name: 'Default (Killer Wife)' },
    ],
  },
  {
    id: 'c200',
    name: 'Marciana',
    manufacturer: 'Elysion',
    squad: 'School Circle',
    element: 'Iron',
    weapon: 'SG',
    accent: '#8b5cf6',
    skins: [
      { code: '00', name: 'Default (Teacher)' },
      { code: '01', name: 'School Days' },
    ],
  },
  {
    id: 'c020',
    name: 'Emma',
    manufacturer: 'Elysion',
    squad: 'Absolute',
    element: 'Fire',
    weapon: 'MG',
    accent: '#dc2626',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Color Me Red' },
      { code: '02', name: 'Office Therapy' },
    ],
  },
  {
    id: 'c050',
    name: 'Diesel',
    manufacturer: 'Elysion',
    squad: 'Infinity Rail',
    element: 'Wind',
    weapon: 'MG',
    accent: '#10b981',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Black Sunday' },
    ],
  },
  {
    id: 'c051',
    name: 'Brid',
    manufacturer: 'Elysion',
    squad: 'Infinity Rail',
    element: 'Water',
    weapon: 'AR',
    accent: '#3b82f6',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Black Moon' },
    ],
  },
  {
    id: 'c090',
    name: 'Guillotine',
    manufacturer: 'Elysion',
    squad: 'Extrinsic',
    element: 'Electric',
    weapon: 'MG',
    accent: '#eab308',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Dark Tracer' },
    ],
  },
  {
    id: 'c091',
    name: 'Maiden',
    manufacturer: 'Elysion',
    squad: 'Extrinsic',
    element: 'Electric',
    weapon: 'SG',
    accent: '#ec4899',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Covert Nurse' },
    ],
  },

  // MISSILIS
  {
    id: 'c100',
    name: 'Laplace',
    manufacturer: 'Missilis',
    squad: 'Matis',
    element: 'Iron',
    weapon: 'RL',
    accent: '#eab308',
    skins: [
      { code: '00', name: 'Default (Hero)' },
    ],
  },
  {
    id: 'c101',
    name: 'Drake',
    manufacturer: 'Missilis',
    squad: 'Matis',
    element: 'Fire',
    weapon: 'SG',
    accent: '#ef4444',
    skins: [
      { code: '00', name: 'Default (Villain)' },
      { code: '01', name: 'Villain Racer' },
    ],
  },
  {
    id: 'c102',
    name: 'Maxwell',
    manufacturer: 'Missilis',
    squad: 'Matis',
    element: 'Iron',
    weapon: 'SR',
    accent: '#a855f7',
    skins: [
      { code: '00', name: 'Default (Mechanic)' },
      { code: '01', name: 'Mechanic White' },
    ],
  },
  {
    id: 'c190',
    name: 'Liter',
    manufacturer: 'Missilis',
    squad: 'Mighty Tools',
    element: 'Iron',
    weapon: 'SMG',
    accent: '#f59e0b',
    skins: [
      { code: '00', name: 'Default (Builder)' },
      { code: '01', name: 'Guard Fish' },
    ],
  },
  {
    id: 'c280',
    name: 'Tia',
    manufacturer: 'Missilis',
    squad: 'Dazzling Pearl',
    element: 'Iron',
    weapon: 'RL',
    accent: '#fb923c',
    skins: [
      { code: '00', name: 'Default (School)' },
    ],
  },
  {
    id: 'c281',
    name: 'Naga',
    manufacturer: 'Missilis',
    squad: 'Dazzling Pearl',
    element: 'Electric',
    weapon: 'SG',
    accent: '#818cf8',
    skins: [
      { code: '00', name: 'Default (School)' },
      { code: '01', name: 'Last Girlhood' },
    ],
  },
  {
    id: 'c390',
    name: 'Elegg',
    manufacturer: 'Missilis',
    squad: 'Electric Shock',
    element: 'Electric',
    weapon: 'MG',
    accent: '#facc15',
    skins: [
      { code: '00', name: 'Default (BOOM)' },
    ],
  },
  {
    id: 'c140',
    name: 'Pepper',
    manufacturer: 'Missilis',
    squad: 'Seraphim',
    element: 'Wind',
    weapon: 'SG',
    accent: '#fb7185',
    skins: [
      { code: '00', name: 'Default (Doctor)' },
      { code: '01', name: 'Ocean Vitamin (Swimsuit)' },
    ],
  },
  {
    id: 'c240',
    name: 'Guilty',
    manufacturer: 'Missilis',
    squad: 'Real Kindness',
    element: 'Wind',
    weapon: 'SG',
    accent: '#4ade80',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Wave of Disbelief' },
    ],
  },

  // TETRA
  {
    id: 'c012',
    name: 'Anis',
    manufacturer: 'Tetra',
    squad: 'Counters',
    element: 'Iron',
    weapon: 'RL',
    accent: '#eab308',
    skins: [
      { code: '00', name: 'Default (Counters)' },
    ],
  },
  {
    id: 'c017',
    name: 'Anis: Sparkling Summer',
    manufacturer: 'Tetra',
    squad: 'Counters',
    element: 'Electric',
    weapon: 'SG',
    accent: '#facc15',
    skins: [
      { code: '00', name: 'Default (Summer)' },
    ],
  },
  {
    id: 'c040',
    name: 'Alice',
    manufacturer: 'Tetra',
    squad: 'Unlimited',
    element: 'Fire',
    weapon: 'SR',
    accent: '#f472b6',
    skins: [
      { code: '00', name: 'Default (Wonderland)' },
      { code: '01', name: 'Sweet Home' },
    ],
  },
  {
    id: 'c350',
    name: 'Blanc',
    manufacturer: 'Tetra',
    squad: '777',
    element: 'Wind',
    weapon: 'AR',
    accent: '#f8fafc',
    skins: [
      { code: '00', name: 'Default (White Bunny)' },
      { code: '01', name: 'White Rabbit' },
    ],
  },
  {
    id: 'c351',
    name: 'Noir',
    manufacturer: 'Tetra',
    squad: '777',
    element: 'Wind',
    weapon: 'SG',
    accent: '#a855f7',
    skins: [
      { code: '00', name: 'Default (Black Bunny)' },
      { code: '01', name: 'Black Rabbit' },
    ],
  },
  {
    id: 'c290',
    name: 'Viper',
    manufacturer: 'Tetra',
    squad: 'Exotic',
    element: 'Water',
    weapon: 'SG',
    accent: '#ec4899',
    skins: [
      { code: '00', name: 'Default' },
      { code: '01', name: 'Toxic Rabbit' },
      { code: '02', name: 'Shine of Love' },
    ],
  },
  {
    id: 'c180',
    name: 'Rupee',
    manufacturer: 'Tetra',
    squad: 'Talentum',
    element: 'Iron',
    weapon: 'AR',
    accent: '#fbbf24',
    skins: [
      { code: '00', name: 'Default (Shopaholic)' },
      { code: '01', name: 'Rabbit Deluxe' },
    ],
  },
  {
    id: 'c260',
    name: 'Sakura',
    manufacturer: 'Tetra',
    squad: 'Underworld Queen',
    element: 'Fire',
    weapon: 'SR',
    accent: '#f43f5e',
    skins: [
      { code: '00', name: 'Default (Seimeikai)' },
      { code: '01', name: 'Midnight Stealth' },
    ],
  },
  {
    id: 'c261',
    name: 'Rosanna',
    manufacturer: 'Tetra',
    squad: 'Underworld Queen',
    element: 'Electric',
    weapon: 'MG',
    accent: '#a855f7',
    skins: [
      { code: '00', name: 'Default (Hedonia)' },
    ],
  },
  {
    id: 'c041',
    name: 'Ludmilla',
    manufacturer: 'Tetra',
    squad: 'Unlimited',
    element: 'Water',
    weapon: 'SMG',
    accent: '#38bdf8',
    skins: [
      { code: '00', name: 'Default (Queen)' },
    ],
  },
  {
    id: 'c044',
    name: 'Ludmilla: Winter Owner',
    manufacturer: 'Tetra',
    squad: 'Unlimited',
    element: 'Water',
    weapon: 'MG',
    accent: '#60a5fa',
    skins: [
      { code: '00', name: 'Default (Hot Spring)' },
    ],
  },
  {
    id: 'c120',
    name: 'Volume',
    manufacturer: 'Tetra',
    squad: 'Prima Donna',
    element: 'Wind',
    weapon: 'SMG',
    accent: '#ef4444',
    skins: [
      { code: '00', name: 'Default (Hip-hop)' },
    ],
  },
  {
    id: 'c121',
    name: 'Noise',
    manufacturer: 'Tetra',
    squad: 'Prima Donna',
    element: 'Electric',
    weapon: 'RL',
    accent: '#c084fc',
    skins: [
      { code: '00', name: 'Default (Diva)' },
      { code: '01', name: 'Classic Diva' },
    ],
  },

  // ABNORMAL (COLLAB)
  {
    id: 'c810',
    name: '2B',
    manufacturer: 'Abnormal',
    squad: 'YoRHa',
    element: 'Fire',
    weapon: 'AR',
    accent: '#e2e8f0',
    skins: [
      { code: '00', name: 'Default (YoRHa)' },
      { code: '01', name: 'Metamorphic Damage' },
      { code: '02', name: 'YoRHa Uniform 1' },
    ],
  },
  {
    id: 'c811',
    name: 'A2',
    manufacturer: 'Abnormal',
    squad: 'YoRHa',
    element: 'Fire',
    weapon: 'RL',
    accent: '#cbd5e1',
    skins: [
      { code: '00', name: 'Default (YoRHa Prototype)' },
      { code: '01', name: 'Metamorphic Damage' },
    ],
  },
  {
    id: 'c800',
    name: 'Makima',
    manufacturer: 'Abnormal',
    squad: 'Devil Hunter',
    element: 'Water',
    weapon: 'SMG',
    accent: '#e11d48',
    skins: [
      { code: '00', name: 'Default (Coat)' },
      { code: '01', name: 'Coat Off' },
    ],
  },
  {
    id: 'c801',
    name: 'Power',
    manufacturer: 'Abnormal',
    squad: 'Devil Hunter',
    element: 'Fire',
    weapon: 'RL',
    accent: '#fb7185',
    skins: [
      { code: '00', name: 'Default (Blood Fiend)' },
    ],
  },
  {
    id: 'c820',
    name: 'Emilia',
    manufacturer: 'Abnormal',
    squad: 'Re:Zero',
    element: 'Water',
    weapon: 'RL',
    accent: '#c084fc',
    skins: [
      { code: '00', name: 'Default (Half-Elf)' },
      { code: '01', name: 'Clumsy Maid' },
    ],
  },
  {
    id: 'c821',
    name: 'Rem',
    manufacturer: 'Abnormal',
    squad: 'Re:Zero',
    element: 'Water',
    weapon: 'MG',
    accent: '#60a5fa',
    skins: [
      { code: '00', name: 'Default (Maid)' },
    ],
  },
];
