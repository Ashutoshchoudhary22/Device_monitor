interface LocationAddressProps {
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  className?: string;
}

export default function LocationAddress({
  address,
  latitude,
  longitude,
  className = '',
}: LocationAddressProps) {
  const hasCoords = latitude != null && longitude != null;

  return (
    <div className={className}>
      {address ? (
        <p className="text-sm font-medium text-gray-800 dark:text-gray-200">{address}</p>
      ) : (
        <p className="text-sm text-gray-400">Resolving address...</p>
      )}
      {hasCoords && (
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
          {latitude!.toFixed(6)}, {longitude!.toFixed(6)}
        </p>
      )}
    </div>
  );
}
