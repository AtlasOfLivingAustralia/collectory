import type { ImageRef } from '../../api/types';

interface EntityImageProps {
  imageRef?: ImageRef;
  logoRef?: ImageRef;
  entityName: string;
}

export default function EntityImage({ imageRef, logoRef, entityName }: EntityImageProps) {
  const img = imageRef ?? logoRef;

  const src = img?.uri ?? img?.file;
  if (!img || !src) return null;

  return (
    <section className="public-metadata">
      <img
        style={{ maxWidth: '100%', maxHeight: 350 }}
        alt={img.caption ?? entityName}
        src={src}
      />
      {img.caption && <p className="caption">{img.caption}</p>}
      {img.attribution && <p className="caption">{img.attribution}</p>}
      {img.copyright && <p className="caption">{img.copyright}</p>}
    </section>
  );
}
