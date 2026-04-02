import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listingService } from "../api/listingService";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { useToast } from "../components/ToastProvider";
import { useAuth } from "../auth/AuthContext";
import { useDebounce } from "../hooks/useDebounce";
import { formatCurrency } from "../utils/formatting";

const initialFilters = {
  keyword: "",
  neighborhood: "",
  radius: "",
  lat: "",
  lng: "",
  priceMin: "",
  priceMax: "",
  sqftMin: "",
  sqftMax: "",
  tags: "",
  availFrom: "",
  availTo: "",
  sort: "newest",
};

export default function HomePage() {
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const [filters, setFilters] = useState(initialFilters);
  const [openFilters, setOpenFilters] = useState(false);
  const [loading, setLoading] = useState(true);
  const [results, setResults] = useState([]);
  const [meta, setMeta] = useState({
    page: 0,
    pageSize: 20,
    totalItems: 0,
    totalPages: 0,
  });
  const [trending, setTrending] = useState([]);
  const [recentSearches, setRecentSearches] = useState([]);
  const debouncedKeyword = useDebounce(filters.keyword, 300);

  const queryParams = useMemo(() => {
    const tags = filters.tags
      .split(",")
      .map((tag) => tag.trim())
      .filter(Boolean);

    return {
      keyword: debouncedKeyword || undefined,
      neighborhood: filters.neighborhood || undefined,
      radius: toNumber(filters.radius),
      lat: toNumber(filters.lat),
      lng: toNumber(filters.lng),
      priceMin: toNumber(filters.priceMin),
      priceMax: toNumber(filters.priceMax),
      sqftMin: toNumber(filters.sqftMin),
      sqftMax: toNumber(filters.sqftMax),
      tags: tags.length ? tags : undefined,
      availFrom: filters.availFrom || undefined,
      availTo: filters.availTo || undefined,
      sort: filters.sort,
      page: meta.page,
      pageSize: meta.pageSize,
    };
  }, [debouncedKeyword, filters, meta.page, meta.pageSize]);

  useEffect(() => {
    fetchTrending();
  }, []);

  useEffect(() => {
    fetchListings();
  }, [queryParams]);

  useEffect(() => {
    if (!isAuthenticated) {
      setRecentSearches([]);
      return;
    }
    listingService
      .recentSearches()
      .then((rows) => setRecentSearches(rows || []))
      .catch(() => {
        setRecentSearches([]);
      });
  }, [isAuthenticated]);

  const fetchListings = async () => {
    setLoading(true);
    try {
      const response = await listingService.searchWithMeta(queryParams);
      setResults(response.data || []);
      setMeta((prev) => ({
        ...prev,
        totalItems: response.meta.totalItems ?? response.data.length,
        totalPages: response.meta.totalPages ?? 1,
      }));
    } catch (error) {
      showToast(error?.message || "Failed to load listings", "error");
    } finally {
      setLoading(false);
    }
  };

  const fetchTrending = async () => {
    try {
      const rows = await listingService.trending(10);
      setTrending(rows || []);
    } catch {
      setTrending([]);
    }
  };

  const applyRecentSearch = (recent) => {
    try {
      const parsed = recent.filters ? JSON.parse(recent.filters) : {};
      setFilters((prev) => ({
        ...prev,
        keyword: recent.query || "",
        neighborhood: parsed.neighborhood || "",
        radius: parsed.radiusMiles || "",
        lat: parsed.lat || "",
        lng: parsed.lng || "",
        priceMin: parsed.priceMin || "",
        priceMax: parsed.priceMax || "",
        sqftMin: parsed.sqftMin || "",
        sqftMax: parsed.sqftMax || "",
        tags: Array.isArray(parsed.tags) ? parsed.tags.join(", ") : "",
        availFrom: parsed.availFrom || "",
        availTo: parsed.availTo || "",
        sort: parsed.sort || "newest",
      }));
      setMeta((prev) => ({ ...prev, page: 0 }));
    } catch {
      // ignore parse failure
    }
  };

  return (
    <div className="page-grid">
      <Card className="hero-panel">
        <div className="hero-content">
          <h1>Discover Heritage Listings</h1>
          <p>
            Search crafts, spaces, and experiences with precision filters and
            live trend signals.
          </p>
          <div className="search-inline">
            <input
              placeholder="Search by keyword..."
              value={filters.keyword}
              onChange={(event) => {
                setMeta((prev) => ({ ...prev, page: 0 }));
                setFilters((prev) => ({
                  ...prev,
                  keyword: event.target.value,
                }));
              }}
            />
            <Button
              variant="secondary"
              onClick={() => setOpenFilters((v) => !v)}
            >
              {openFilters ? "Hide Filters" : "Show Filters"}
            </Button>
          </div>
          {openFilters && (
            <div className="filters-grid">
              <label>
                Neighborhood
                <input
                  value={filters.neighborhood}
                  onChange={(e) =>
                    setFilters((prev) => ({
                      ...prev,
                      neighborhood: e.target.value,
                    }))
                  }
                />
              </label>
              <label>
                Radius (miles)
                <input
                  value={filters.radius}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, radius: e.target.value }))
                  }
                />
              </label>
              <label>
                Latitude
                <input
                  value={filters.lat}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, lat: e.target.value }))
                  }
                />
              </label>
              <label>
                Longitude
                <input
                  value={filters.lng}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, lng: e.target.value }))
                  }
                />
              </label>
              <label>
                Price Min
                <input
                  value={filters.priceMin}
                  onChange={(e) =>
                    setFilters((prev) => ({
                      ...prev,
                      priceMin: e.target.value,
                    }))
                  }
                />
              </label>
              <label>
                Price Max
                <input
                  value={filters.priceMax}
                  onChange={(e) =>
                    setFilters((prev) => ({
                      ...prev,
                      priceMax: e.target.value,
                    }))
                  }
                />
              </label>
              <label>
                Sqft Min
                <input
                  value={filters.sqftMin}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, sqftMin: e.target.value }))
                  }
                />
              </label>
              <label>
                Sqft Max
                <input
                  value={filters.sqftMax}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, sqftMax: e.target.value }))
                  }
                />
              </label>
              <label>
                Tags (comma separated)
                <input
                  value={filters.tags}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, tags: e.target.value }))
                  }
                />
              </label>
              <label>
                Available From
                <input
                  type="date"
                  value={filters.availFrom}
                  onChange={(e) =>
                    setFilters((prev) => ({
                      ...prev,
                      availFrom: e.target.value,
                    }))
                  }
                />
              </label>
              <label>
                Available To
                <input
                  type="date"
                  value={filters.availTo}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, availTo: e.target.value }))
                  }
                />
              </label>
              <label>
                Sort
                <select
                  value={filters.sort}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, sort: e.target.value }))
                  }
                >
                  <option value="newest">Newest</option>
                  <option value="price_asc">Price ↑</option>
                  <option value="price_desc">Price ↓</option>
                  <option value="distance">Distance</option>
                  <option value="popularity">Popularity</option>
                </select>
              </label>
            </div>
          )}
        </div>
      </Card>

      <section>
        <div className="section-heading">
          <h2>Results</h2>
          <span>{results.length} listings</span>
        </div>
        {loading ? (
          <div className="listing-grid">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="listing-skeleton" />
            ))}
          </div>
        ) : results.length === 0 ? (
          <EmptyState
            title="No listings found"
            message="Try adjusting filters or removing location limits."
          />
        ) : (
          <div className="listing-grid">
            {results.map((listing) => (
              <Link
                to={`/listings/${listing.id}`}
                key={listing.id}
                className="listing-card glass-card"
              >
                <div className="listing-topline">
                  <h3>{listing.title}</h3>
                  {Number(listing.trending_score) > 0 && (
                    <span className="trend-pill">🔥 Trending</span>
                  )}
                </div>
                <p className="listing-neighborhood">
                  {listing.neighborhood || "Neighborhood n/a"}
                </p>
                <strong className="listing-price">
                  {formatCurrency(listing.price)}
                </strong>
                <div className="tag-list">
                  {(listing.tags || []).slice(0, 4).map((tag) => (
                    <span key={tag} className="tag-chip">
                      {tag}
                    </span>
                  ))}
                </div>
                {filters.sort === "distance" &&
                  listing.distance_miles != null && (
                    <small>
                      {Number(listing.distance_miles).toFixed(2)} miles away
                    </small>
                  )}
              </Link>
            ))}
          </div>
        )}

        {meta.totalPages > 1 && (
          <div className="pagination-controls">
            <Button
              variant="ghost"
              disabled={meta.page <= 0}
              onClick={() => setMeta((prev) => ({ ...prev, page: prev.page - 1 }))}
            >
              Previous
            </Button>
            <span className="pagination-info">
              Page {meta.page + 1} of {meta.totalPages}
            </span>
            <Button
              variant="ghost"
              disabled={meta.page + 1 >= meta.totalPages}
              onClick={() => setMeta((prev) => ({ ...prev, page: prev.page + 1 }))}
            >
              Next
            </Button>
          </div>
        )}
      </section>

      <section>
        <div className="section-heading">
          <h2>Trending This Week</h2>
        </div>
        <div className="horizontal-scroll">
          {trending.map((listing) => (
            <Link
              to={`/listings/${listing.id}`}
              key={listing.id}
              className="trend-card glass-card"
            >
              <h4>{listing.title}</h4>
              <p>{formatCurrency(listing.price)}</p>
            </Link>
          ))}
        </div>
      </section>

      {isAuthenticated && (
        <section>
          <div className="section-heading">
            <h2>Recent Searches</h2>
          </div>
          {recentSearches.length === 0 ? (
            <EmptyState
              title="No recent searches"
              message="Your latest 20 searches will appear here."
            />
          ) : (
            <div className="recent-search-grid">
              {recentSearches.map((search) => (
                <button
                  key={search.id}
                  className="recent-search-btn"
                  onClick={() => applyRecentSearch(search)}
                >
                  {search.query || "Untitled Search"}
                </button>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}

function toNumber(value) {
  if (value === "" || value == null) {
    return undefined;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}
