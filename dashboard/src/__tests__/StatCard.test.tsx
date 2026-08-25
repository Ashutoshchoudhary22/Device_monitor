import { render, screen } from '@testing-library/react';
import StatCard from '@/components/StatCard';

describe('StatCard', () => {
  it('renders title and value', () => {
    render(<StatCard title="Total Devices" value={5} />);
    expect(screen.getByText('Total Devices')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('renders subtitle when provided', () => {
    render(<StatCard title="Battery" value="85%" subtitle="Charging" />);
    expect(screen.getByText('Charging')).toBeInTheDocument();
  });
});
