export type ContributionPosterData = {
  displayName: string;
  lifetimePoints: number;
  currentMonthPoints: number;
  monthLabel: string;
  contributionLabels: string[];
};

export type ContributionPosterResult = {
  blob: Blob;
  file: File;
  filename: string;
};

function roundedRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  context.beginPath();
  context.roundRect(x, y, width, height, radius);
  context.fill();
}

function fitText(context: CanvasRenderingContext2D, value: string, maxWidth: number, initialSize: number, minimumSize: number) {
  let size = initialSize;
  while (size > minimumSize) {
    context.font = `700 ${size}px system-ui, sans-serif`;
    if (context.measureText(value).width <= maxWidth) return;
    size -= 2;
  }
  context.font = `700 ${minimumSize}px system-ui, sans-serif`;
}

function canvasBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('The Civic Card image could not be created.')), 'image/png'));
}

export async function createContributionPoster(data: ContributionPosterData): Promise<ContributionPosterResult> {
  const canvas = document.createElement('canvas');
  canvas.width = 1080;
  canvas.height = 1350;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Image creation is not supported in this browser.');

  const gradient = context.createLinearGradient(0, 0, 1080, 1350);
  gradient.addColorStop(0, '#0e2340');
  gradient.addColorStop(.58, '#123a50');
  gradient.addColorStop(1, '#15918b');
  context.fillStyle = gradient;
  context.fillRect(0, 0, canvas.width, canvas.height);

  context.fillStyle = 'rgba(248, 165, 54, .96)';
  context.beginPath();
  context.arc(970, 120, 220, 0, Math.PI * 2);
  context.fill();
  context.fillStyle = 'rgba(255, 255, 255, .08)';
  context.beginPath();
  context.arc(60, 1240, 260, 0, Math.PI * 2);
  context.fill();

  context.fillStyle = '#ffffff';
  context.font = '800 34px system-ui, sans-serif';
  context.letterSpacing = '8px';
  context.fillText('SEEWIK', 80, 100);
  context.letterSpacing = '0px';
  context.fillStyle = '#a9e3dd';
  context.font = '700 26px system-ui, sans-serif';
  context.fillText('MY CIVIC CARD', 80, 184);

  context.fillStyle = '#ffffff';
  fitText(context, data.displayName, 920, 74, 42);
  context.fillText(data.displayName, 80, 280);
  context.fillStyle = '#b9cad8';
  context.font = '400 29px system-ui, sans-serif';
  context.fillText('A personal record of contributions made through Seewik', 80, 333);

  context.fillStyle = 'rgba(255, 255, 255, .1)';
  roundedRect(context, 80, 405, 920, 260, 36);
  context.fillStyle = '#ffffff';
  context.font = '800 116px system-ui, sans-serif';
  context.fillText(String(Math.max(0, data.lifetimePoints)), 125, 548);
  context.fillStyle = '#a9e3dd';
  context.font = '700 28px system-ui, sans-serif';
  context.fillText('LIFETIME CIVIC POINTS', 130, 598);

  context.fillStyle = '#ffffff';
  context.font = '800 58px system-ui, sans-serif';
  context.fillText(String(Math.max(0, data.currentMonthPoints)), 700, 525);
  context.fillStyle = '#b9cad8';
  context.font = '600 23px system-ui, sans-serif';
  context.fillText(`${data.monthLabel || 'This month'} points`, 700, 570);

  context.fillStyle = '#ffffff';
  context.font = '700 34px system-ui, sans-serif';
  context.fillText('Contribution record', 80, 760);
  const safeLabels = data.contributionLabels.filter(Boolean).slice(0, 4);
  const labels = safeLabels.length > 0 ? safeLabels : ['Civic action recorded'];
  labels.forEach((label, index) => {
    const y = 825 + index * 92;
    context.fillStyle = index % 2 ? 'rgba(255, 255, 255, .09)' : 'rgba(248, 165, 54, .15)';
    roundedRect(context, 80, y, 920, 68, 22);
    context.fillStyle = '#ffffff';
    context.font = '600 27px system-ui, sans-serif';
    context.fillText(`✓  ${label}`, 112, y + 44);
  });

  context.fillStyle = '#b9cad8';
  context.font = '500 23px system-ui, sans-serif';
  context.fillText('A Seewik record of contributions · Not a government document', 80, 1260);
  context.fillStyle = '#a9e3dd';
  context.font = '600 21px system-ui, sans-serif';
  context.fillText('Created on this device. No public poster URL was made.', 80, 1304);

  const blob = await canvasBlob(canvas);
  const safeName = data.displayName.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 32) || 'citizen';
  const filename = `seewik-civic-card-${safeName}.png`;
  return { blob, file: new File([blob], filename, { type: 'image/png' }), filename };
}
