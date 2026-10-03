export function Ico({ name }: { name: string }) {
  const url = `/brand/${name}`;
  return (
    <span
      className="qh-ico"
      style={{ maskImage: `url("${url}")`, WebkitMaskImage: `url("${url}")` }}
      aria-hidden="true"
    />
  );
}
