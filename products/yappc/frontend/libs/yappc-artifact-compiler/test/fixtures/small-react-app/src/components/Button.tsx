interface ButtonProps {
  readonly label: string;
}

export function Button(props: ButtonProps): JSX.Element {
  return <button>{props.label}</button>;
}
