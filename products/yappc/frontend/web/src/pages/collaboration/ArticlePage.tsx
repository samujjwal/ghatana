import React from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';

interface ArticleAuthor {
  id: string;
  name: string;
  avatarUrl: string;
}

interface ArticleHeading {
  id: string;
  text: string;
  level: number;
}

interface RelatedArticle {
  id: string;
  title: string;
  summary: string;
  updatedAt: string;
}

interface ArticleSection {
  id: string;
  heading: string;
  level: number;
  content: string;
}

interface ArticleData {
  id: string;
  title: string;
  author: ArticleAuthor;
  createdAt: string;
  updatedAt: string;
  tags: string[];
  headings: ArticleHeading[];
  sections: ArticleSection[];
  relatedArticles: RelatedArticle[];
}

const authHeaders = (): Record<string, string> => ({
  Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
});

/**
 * ArticlePage — Knowledge base article with TOC sidebar and related articles.
 *
 * @doc.type component
 * @doc.purpose Knowledge base article detail view
 * @doc.layer product
 */
const ArticlePage: React.FC = () => {
  const { articleId } = useParams<{ articleId: string }>();

  const { data: article, isLoading, error } = useQuery<ArticleData>({
    queryKey: ['article', articleId],
    queryFn: async () => {
      const res = await fetch(`/api/articles/${articleId}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load article');
      return res.json() as Promise<ArticleData>;
    },
    enabled: !!articleId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          {error instanceof Error ? error.message : 'Failed to load article'}
        </div>
      </div>
    );
  }

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString([], { year: 'numeric', month: 'long', day: 'numeric' });

  return (
    <div className="p-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-zinc-500 mb-6">
        <Link to="/collaboration/articles" className="hover:text-zinc-300 transition-colors">Knowledge Base</Link>
        <span>/</span>
        <span className="text-zinc-300 truncate max-w-xs">{article?.title ?? 'Article'}</span>
      </nav>

      <div className="flex gap-8">
        {/* TOC sidebar */}
        <aside className="hidden lg:block w-56 shrink-0">
          <div className="sticky top-6">
            <h3 className="text-xs font-semibold text-zinc-500 uppercase tracking-wider mb-3">On This Page</h3>
            <nav className="space-y-1">
              {(article?.headings ?? []).map((h) => (
                <a
                  key={h.id}
                  href={`#${h.id}`}
                  className="block text-sm text-zinc-400 hover:text-zinc-200 transition-colors truncate"
                  style={{ paddingLeft: `${(h.level - 1) * 12}px` }}
                >
                  {h.text}
                </a>
              ))}
            </nav>

            {/* Related articles in sidebar */}
            {(article?.relatedArticles ?? []).length > 0 && (
              <div className="mt-8">
                <h3 className="text-xs font-semibold text-zinc-500 uppercase tracking-wider mb-3">Related</h3>
                <div className="space-y-2">
                  {article?.relatedArticles.map((ra) => (
                    <Link
                      key={ra.id}
                      to={`/collaboration/articles/${ra.id}`}
                      className="block p-2 rounded-lg hover:bg-zinc-800/50 transition-colors"
                    >
                      <p className="text-sm text-zinc-300 font-medium truncate">{ra.title}</p>
                      <p className="text-xs text-zinc-500 truncate">{ra.summary}</p>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </div>
        </aside>

        {/* Main article content */}
        <article className="flex-1 min-w-0">
          {/* Header */}
          <header className="mb-8">
            <h1 className="text-3xl font-bold text-zinc-100 mb-4">{article?.title}</h1>
            <div className="flex flex-wrap items-center gap-4 text-sm text-zinc-400">
              <div className="flex items-center gap-2">
                <img
                  src={article?.author.avatarUrl}
                  alt={article?.author.name}
                  className="w-6 h-6 rounded-full bg-zinc-800"
                />
                <span>{article?.author.name}</span>
              </div>
              <span className="text-zinc-600">·</span>
              <span>Published {article?.createdAt ? formatDate(article.createdAt) : '—'}</span>
              {article?.updatedAt && article.updatedAt !== article.createdAt && (
                <>
                  <span className="text-zinc-600">·</span>
                  <span>Updated {formatDate(article.updatedAt)}</span>
                </>
              )}
            </div>
            {(article?.tags ?? []).length > 0 && (
              <div className="flex flex-wrap gap-2 mt-3">
                {article?.tags.map((tag) => (
                  <span key={tag} className="px-2 py-0.5 text-xs font-medium rounded-full bg-zinc-800 text-zinc-400">
                    {tag}
                  </span>
                ))}
              </div>
            )}
          </header>

          {/* Markdown-like content sections */}
          <div className="prose-invert max-w-none space-y-8">
            {(article?.sections ?? []).map((section) => {
              const HeadingTag = section.level === 1
                ? 'h2'
                : section.level === 2
                  ? 'h3'
                  : 'h4';
              const headingSize = section.level === 1
                ? 'text-xl'
                : section.level === 2
                  ? 'text-lg'
                  : 'text-base';

              return (
                <section key={section.id} id={section.id}>
                  <HeadingTag className={`${headingSize} font-semibold text-zinc-200 mb-3`}>
                    {section.heading}
                  </HeadingTag>
                  <div
                    className="text-sm text-zinc-400 leading-relaxed whitespace-pre-wrap"
                  >
                    {section.content}
                  </div>
                </section>
              );
            })}
          </div>

          {/* Related articles (mobile) */}
          {(article?.relatedArticles ?? []).length > 0 && (
            <div className="lg:hidden mt-12 pt-8 border-t border-zinc-800">
              <h3 className="text-sm font-semibold text-zinc-300 mb-4">Related Articles</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                {article?.relatedArticles.map((ra) => (
                  <Link
                    key={ra.id}
                    to={`/collaboration/articles/${ra.id}`}
                    className="p-4 bg-zinc-900 border border-zinc-800 rounded-lg hover:border-zinc-700 transition-colors"
                  >
                    <p className="text-sm text-zinc-200 font-medium">{ra.title}</p>
                    <p className="text-xs text-zinc-500 mt-1 line-clamp-2">{ra.summary}</p>
                  </Link>
                ))}
              </div>
            </div>
          )}
        </article>
      </div>
    </div>
  );
};

export default ArticlePage;
