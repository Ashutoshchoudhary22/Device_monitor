interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  color?: string;
}

export default function StatCard({ title, value, subtitle, color = 'text-primary' }: StatCardProps) {
  return (
    <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 shadow-sm">
      <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{title}</p>
      <p className={`mt-2 text-3xl font-bold ${color}`}>{value}</p>
      {subtitle && (
        <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">{subtitle}</p>
      )}
    </div>
  );
}
