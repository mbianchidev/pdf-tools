import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { expect, test } from 'vitest';
import ToolTopbar from './ToolTopbar';

const CurrentPath = () => <p data-testid="path">{useLocation().pathname}</p>;

const renderTopbar = () => render(
  <MemoryRouter initialEntries={['/merge']}>
    <ToolTopbar />
    <Routes>
      <Route path="*" element={<CurrentPath />} />
    </Routes>
  </MemoryRouter>,
);

const openSearch = async (user) => {
  await user.click(screen.getByRole('button', { name: 'Choose a tool' }));
  const search = screen.getByRole('searchbox', { name: 'Search tools' });
  await waitFor(() => expect(search).toHaveFocus());
  return search;
};

test('filters tools by title, description, and group', async () => {
  const user = userEvent.setup();
  renderTopbar();
  const search = await openSearch(user);

  await user.type(search, 'markdown');

  expect(screen.getByRole('link', { name: /Markdown to PDF/ })).toBeVisible();
  expect(screen.getByRole('link', { name: /PDF to Markdown/ })).toBeVisible();
  expect(screen.queryByRole('link', { name: /Merge PDFs/ })).toBeNull();
  expect(screen.queryByText('Organize')).toBeNull();

  await user.clear(search);
  await user.type(search, 'word');
  expect(screen.getByRole('link', { name: /PDF to Word/ })).toBeVisible();
  expect(screen.queryByRole('link', { name: /Protect PDF/ })).toBeNull();

  await user.clear(search);
  await user.type(search, 'zzz-no-tool');
  expect(screen.getByRole('status')).toHaveTextContent(/No tools match/);
  expect(
    document.querySelectorAll('#tool-switcher-results a'),
  ).toHaveLength(0);
});

test('opens the first match with Enter and resets the search', async () => {
  const user = userEvent.setup();
  renderTopbar();
  const search = await openSearch(user);

  await user.type(search, 'compress{Enter}');

  expect(screen.getByTestId('path')).toHaveTextContent('/compress');
  expect(screen.queryByRole('searchbox')).toBeNull();
  await openSearch(user);
  expect(screen.getByRole('searchbox', { name: 'Search tools' })).toHaveValue('');
});
